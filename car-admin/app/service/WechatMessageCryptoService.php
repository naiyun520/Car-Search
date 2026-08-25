<?php

declare(strict_types=1);

namespace app\service;

final class WechatMessageCryptoService
{
    public function __construct(
        private string $token,
        private string $encodingAesKey,
        private string $appId
    ) {
        if ($this->token === '' || !preg_match('/^[A-Za-z0-9]{3,32}$/', $this->token)) {
            throw new \RuntimeException('微信消息推送 Token 未正确配置');
        }
        if (!preg_match('/^[A-Za-z0-9]{43}$/', $this->encodingAesKey)) {
            throw new \RuntimeException('微信消息推送 EncodingAESKey 未正确配置');
        }
        if ($this->appId === '') throw new \RuntimeException('微信 AppID 未配置');
    }

    public function verifyUrl(string $signature, string $timestamp, string $nonce): bool
    {
        return $signature !== '' && hash_equals($this->signature($timestamp, $nonce), $signature);
    }

    public function decryptEcho(string $encryptedEcho, string $messageSignature, string $timestamp, string $nonce): string
    {
        if ($encryptedEcho === '' || $messageSignature === '') {
            throw new \InvalidArgumentException('微信消息地址验证参数缺失');
        }
        if (!hash_equals($this->signature($timestamp, $nonce, $encryptedEcho), $messageSignature)) {
            throw new \InvalidArgumentException('微信消息地址验证签名失败');
        }
        return $this->decrypt($encryptedEcho);
    }

    public function decryptEnvelope(string $body, string $messageSignature, string $timestamp, string $nonce): array
    {
        try {
            $envelope = json_decode($body, true, 16, JSON_THROW_ON_ERROR);
        } catch (\JsonException) {
            throw new \InvalidArgumentException('微信消息请求格式无效');
        }
        if (!is_array($envelope)) throw new \InvalidArgumentException('微信消息请求格式无效');
        $encrypted = trim((string) ($envelope['Encrypt'] ?? $envelope['encrypt'] ?? ''));
        if ($encrypted === '' || $messageSignature === '') throw new \InvalidArgumentException('微信消息密文或签名缺失');
        if (!hash_equals($this->signature($timestamp, $nonce, $encrypted), $messageSignature)) {
            throw new \InvalidArgumentException('微信消息签名校验失败');
        }

        $plain = $this->decrypt($encrypted);
        try {
            $event = json_decode($plain, true, 32, JSON_THROW_ON_ERROR);
        } catch (\JsonException) {
            throw new \InvalidArgumentException('微信消息明文格式无效');
        }
        if (!is_array($event)) throw new \InvalidArgumentException('微信消息明文格式无效');
        return $event;
    }

    public function encryptResponse(array $response, ?string $timestamp = null, ?string $nonce = null): array
    {
        $timestamp = $timestamp ?: (string) time();
        $nonce = $nonce ?: bin2hex(random_bytes(8));
        $plain = json_encode($response, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR);
        $encrypted = $this->encrypt($plain);
        return [
            'Encrypt'=>$encrypted,
            'MsgSignature'=>$this->signature($timestamp, $nonce, $encrypted),
            'TimeStamp'=>$timestamp,
            'Nonce'=>$nonce,
        ];
    }

    private function signature(string $timestamp, string $nonce, ?string $encrypted = null): string
    {
        $parts = [$this->token, $timestamp, $nonce];
        if ($encrypted !== null) $parts[] = $encrypted;
        sort($parts, SORT_STRING);
        return sha1(implode('', $parts));
    }

    private function decrypt(string $encrypted): string
    {
        $cipher = base64_decode($encrypted, true);
        if ($cipher === false || $cipher === '') throw new \InvalidArgumentException('微信消息密文无效');
        $key = $this->aesKey();
        $padded = openssl_decrypt($cipher, 'AES-256-CBC', $key, OPENSSL_RAW_DATA | OPENSSL_ZERO_PADDING, substr($key, 0, 16));
        if (!is_string($padded) || $padded === '') throw new \InvalidArgumentException('微信消息解密失败');
        $plain = $this->unpad($padded);
        if (strlen($plain) < 20) throw new \InvalidArgumentException('微信消息解密结果无效');
        $length = unpack('Nlength', substr($plain, 16, 4));
        $messageLength = (int) ($length['length'] ?? -1);
        if ($messageLength < 0 || 20 + $messageLength > strlen($plain)) throw new \InvalidArgumentException('微信消息长度无效');
        $message = substr($plain, 20, $messageLength);
        $receivedAppId = substr($plain, 20 + $messageLength);
        if ($receivedAppId === '' || !hash_equals($this->appId, $receivedAppId)) {
            throw new \InvalidArgumentException('微信消息 AppID 校验失败');
        }
        return $message;
    }

    private function encrypt(string $plain): string
    {
        $key = $this->aesKey();
        $payload = random_bytes(16) . pack('N', strlen($plain)) . $plain . $this->appId;
        $payload = $this->pad($payload);
        $cipher = openssl_encrypt($payload, 'AES-256-CBC', $key, OPENSSL_RAW_DATA | OPENSSL_ZERO_PADDING, substr($key, 0, 16));
        if (!is_string($cipher)) throw new \RuntimeException('微信消息响应加密失败');
        return base64_encode($cipher);
    }

    private function aesKey(): string
    {
        $key = base64_decode($this->encodingAesKey . '=', true);
        if ($key === false || strlen($key) !== 32) throw new \RuntimeException('微信消息 EncodingAESKey 无效');
        return $key;
    }

    private function pad(string $value): string
    {
        $amount = 32 - (strlen($value) % 32);
        return $value . str_repeat(chr($amount), $amount);
    }

    private function unpad(string $value): string
    {
        $amount = ord(substr($value, -1));
        if ($amount < 1 || $amount > 32 || strlen($value) < $amount) throw new \InvalidArgumentException('微信消息填充无效');
        $padding = substr($value, -$amount);
        if (!hash_equals(str_repeat(chr($amount), $amount), $padding)) throw new \InvalidArgumentException('微信消息填充无效');
        return substr($value, 0, -$amount);
    }
}

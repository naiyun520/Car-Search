<?php

declare(strict_types=1);

namespace app\service;

class CryptoService
{
    public static function encrypt(array $data): string
    {
        $key = self::key();
        $iv = random_bytes(12);
        $tag = '';
        $cipher = openssl_encrypt(json_encode($data, JSON_UNESCAPED_UNICODE), 'aes-256-gcm', $key, OPENSSL_RAW_DATA, $iv, $tag);
        return base64_encode($iv . $tag . $cipher);
    }

    public static function decrypt(string $payload): array
    {
        $raw = base64_decode($payload, true);
        if ($raw === false || strlen($raw) < 29) {
            return [];
        }
        $plain = openssl_decrypt(substr($raw, 28), 'aes-256-gcm', self::key(), OPENSSL_RAW_DATA, substr($raw, 0, 12), substr($raw, 12, 16));
        return is_string($plain) ? (json_decode($plain, true) ?: []) : [];
    }

    private static function key(): string
    {
        $secret = (string) config('car.data_encrypt_key');
        if (strlen($secret) < 24) {
            throw new \RuntimeException('DATA_ENCRYPT_KEY 未安全配置');
        }
        return hash('sha256', $secret, true);
    }
}

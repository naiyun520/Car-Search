<?php

declare(strict_types=1);

namespace app\controller;

use app\BaseController;
use app\service\ConfigService;
use app\service\WechatMessageCryptoService;
use app\service\WechatRefundEventService;
use think\facade\Log;

final class WechatMessageController extends BaseController
{
    public function verify()
    {
        try {
            $crypto = $this->crypto();
            $signature = trim((string) $this->request->get('signature', ''));
            $messageSignature = trim((string) $this->request->get('msg_signature', ''));
            $timestamp = trim((string) $this->request->get('timestamp', ''));
            $nonce = trim((string) $this->request->get('nonce', ''));
            $echo = (string) $this->request->get('echostr', '');
            // 兼容微信首次配置时的明文校验，以及切换到安全模式后的密文 echostr 校验。
            if ($messageSignature !== '') {
                return $this->plain($crypto->decryptEcho($echo, $messageSignature, $timestamp, $nonce));
            }
            if (!$crypto->verifyUrl($signature, $timestamp, $nonce)) return $this->plain('forbidden', 403);
            return $this->plain($echo);
        } catch (\Throwable $error) {
            Log::warning('wechat message url verification failed | message=' . mb_substr($error->getMessage(), 0, 300));
            return $this->plain('service unavailable', 503);
        }
    }

    public function receive()
    {
        $crypto = null;
        try {
            if (strtolower(trim((string) $this->request->get('encrypt_type', ''))) !== 'aes') {
                return $this->plain('secure mode required', 400);
            }
            $timestamp = trim((string) $this->request->get('timestamp', ''));
            $nonce = trim((string) $this->request->get('nonce', ''));
            $messageSignature = trim((string) $this->request->get('msg_signature', ''));
            // 微信会按指数间隔重推失败事件；不使用时间差拒绝合法重推，重放由事件唯一键幂等处理。
            if (!ctype_digit($timestamp) || $nonce === '') {
                return $this->plain('invalid request', 403);
            }
            $body = (string) $this->request->getContent();
            if ($body === '' || strlen($body) > 131072) return $this->plain('invalid body', 400);
            $crypto = $this->crypto();
            $event = $crypto->decryptEnvelope($body, $messageSignature, $timestamp, $nonce);
            $businessResponse = WechatRefundEventService::handle($event);
            return $this->jsonBody($crypto->encryptResponse($businessResponse));
        } catch (\InvalidArgumentException $error) {
            Log::warning('wechat message rejected | message=' . mb_substr($error->getMessage(), 0, 300));
            return $this->plain('forbidden', 403);
        } catch (\Throwable $error) {
            Log::error('wechat message processing failed | message=' . mb_substr($error->getMessage(), 0, 500));
            if ($crypto instanceof WechatMessageCryptoService) {
                try {
                    return $this->jsonBody($crypto->encryptResponse(['ErrCode'=>1, 'ErrMsg'=>'processing failed']));
                } catch (\Throwable) {
                }
            }
            return $this->plain('service unavailable', 503);
        }
    }

    private function crypto(): WechatMessageCryptoService
    {
        $config = ConfigService::wechatMessage();
        return new WechatMessageCryptoService($config['token'], $config['aes_key'], $config['app_id']);
    }

    private function plain(string $body, int $status = 200)
    {
        return response($body, $status, ['Content-Type'=>'text/plain; charset=utf-8', 'Cache-Control'=>'no-store']);
    }

    private function jsonBody(array $body)
    {
        return response(
            json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
            200,
            ['Content-Type'=>'application/json; charset=utf-8', 'Cache-Control'=>'no-store']
        );
    }
}

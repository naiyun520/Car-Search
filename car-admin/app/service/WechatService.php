<?php

declare(strict_types=1);

namespace app\service;

use think\facade\Db;
use think\facade\Log;

class WechatService
{
    public static function codeToSession(string $code): array
    {
        if ((bool) env('APP_DEBUG', false) && str_starts_with($code, 'dev_')) {
            return ['openid' => 'debug_' . substr(hash('sha256', $code), 0, 24), 'session_key' => 'debug'];
        }
        $wechat = ConfigService::wechat();
        if ($wechat['app_id'] === '' || $wechat['app_secret'] === '') {
            throw new \RuntimeException('微信登录参数尚未配置');
        }
        $query = http_build_query(['appid'=>$wechat['app_id'],'secret'=>$wechat['app_secret'],'js_code'=>$code,'grant_type'=>'authorization_code']);
        $result = self::getJson('https://api.weixin.qq.com/sns/jscode2session?' . $query);
        if (empty($result['openid']) || empty($result['session_key'])) {
            throw new \RuntimeException('微信登录失败');
        }
        return $result;
    }

    public static function queryVirtualOrder(string $openid, string $orderNo, ?int $payEnv = null): array
    {
        $config = VirtualPaymentService::configured();
        if ($payEnv !== null) $config['pay_env'] = $payEnv;
        $result = self::xpay('/xpay/query_order', [
            'openid'=>$openid,
            'env'=>(int) $config['pay_env'],
            'order_id'=>$orderNo,
        ], $config, [268490002]);
        if (!is_array($result['order'] ?? null)) {
            // 微信把“订单尚不存在”和部分参数错误复用为同一错误码；只把明确的数据不存在作为未支付状态。
            if ((int) ($result['errcode'] ?? 0) === 268490002
                && str_contains((string) ($result['errmsg'] ?? ''), '数据不存在')) {
                return ['not_found'=>true];
            }
            Log::warning('wechat virtual order missing | order_no=' . $orderNo);
            throw new \RuntimeException('微信支付订单不存在');
        }
        return $result['order'];
    }

    public static function notifyVirtualGoodsDelivered(string $orderNo, int $payEnv): void
    {
        $config = VirtualPaymentService::configured();
        $config['pay_env'] = $payEnv;
        self::xpay('/xpay/notify_provide_goods', [
            'order_id'=>$orderNo,
            'env'=>(int) $config['pay_env'],
        ], $config);
    }

    public static function refundVirtualOrder(string $openid, string $orderNo, string $refundOrderNo, int $leftFee, int $refundFee, string $reason, int $payEnv): array
    {
        $config = VirtualPaymentService::configured();
        $config['pay_env'] = $payEnv;
        return self::xpay('/xpay/refund_order', [
            'openid'=>$openid,
            'order_id'=>$orderNo,
            'refund_order_id'=>$refundOrderNo,
            'left_fee'=>$leftFee,
            'refund_fee'=>$refundFee,
            'biz_meta'=>'car-admin:' . $orderNo,
            'refund_reason'=>$reason,
            'req_from'=>'1',
            'env'=>(int) $config['pay_env'],
        ], $config, [268490004]);
    }

    private static function xpay(string $path, array $payload, array $config, array $acceptedErrorCodes = []): array
    {
        $body = json_encode($payload, JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
        if ($body === false) throw new \RuntimeException('微信支付请求参数生成失败');
        $paySig = hash_hmac('sha256', $path . '&' . $body, VirtualPaymentService::appKey($config));
        $query = http_build_query(['access_token'=>self::accessToken(), 'pay_sig'=>$paySig]);
        $result = self::postJson('https://api.weixin.qq.com' . $path . '?' . $query, $body);
        if (in_array((int) ($result['errcode'] ?? 0), [40001,40014,42001], true)) {
            $query = http_build_query(['access_token'=>self::accessToken(true), 'pay_sig'=>$paySig]);
            $result = self::postJson('https://api.weixin.qq.com' . $path . '?' . $query, $body);
        }
        $errorCode = (int) ($result['errcode'] ?? -1);
        if ($errorCode !== 0 && !in_array($errorCode, $acceptedErrorCodes, true)) {
            Log::warning('wechat xpay request failed | path=' . $path . ' | order_no=' . ($payload['order_id'] ?? '') . ' | errcode=' . $errorCode . ' | errmsg=' . mb_substr((string) ($result['errmsg'] ?? ''),0,300));
            throw new \RuntimeException('微信支付接口调用失败：' . ($result['errmsg'] ?: (string) $errorCode));
        }
        return $result;
    }

    private static function accessToken(bool $forceRefresh = false): string
    {
        $cached = ConfigService::secure('wechat_access_token');
        $expiresAt = (int) ConfigService::value('wechat_access_token_expires_at', 0);
        if (!$forceRefresh && $cached !== '' && $expiresAt > time() + 120) return $cached;
        $config = ConfigService::wechat();
        if ($config['app_id'] === '' || $config['app_secret'] === '') throw new \RuntimeException('微信 AppID 或 AppSecret 尚未配置');
        $query = http_build_query(['grant_type'=>'client_credential','appid'=>$config['app_id'],'secret'=>$config['app_secret']]);
        $result = self::getJson('https://api.weixin.qq.com/cgi-bin/token?' . $query);
        $token = trim((string) ($result['access_token'] ?? ''));
        if ($token === '') throw new \RuntimeException('微信接口调用凭证获取失败');
        ConfigService::saveSecure('wechat_access_token', $token);
        $now = date('Y-m-d H:i:s');
        Db::name('setting')->strict(false)->replace()->insert([
            'key'=>'wechat_access_token_expires_at',
            'value'=>(string) (time() + max(300, (int) ($result['expires_in'] ?? 7200))),
            'updated_at'=>$now,
        ]);
        return $token;
    }

    private static function getJson(string $url): array
    {
        $curl = curl_init($url);
        curl_setopt_array($curl, [CURLOPT_RETURNTRANSFER=>true,CURLOPT_TIMEOUT=>10,CURLOPT_CONNECTTIMEOUT=>5,CURLOPT_SSL_VERIFYPEER=>true]);
        $body = curl_exec($curl);
        $status = curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        $error = curl_error($curl);
        curl_close($curl);
        if ($body === false || $status !== 200) {
            Log::warning('wechat GET failed | status=' . $status . ' | error=' . mb_substr($error,0,300));
            throw new \RuntimeException('微信服务暂不可用');
        }
        try {
            $decoded = json_decode($body, true, 32, JSON_THROW_ON_ERROR);
        } catch (\JsonException) {
            throw new \RuntimeException('微信服务返回格式异常');
        }
        if (!is_array($decoded)) throw new \RuntimeException('微信服务返回格式异常');
        return $decoded;
    }

    private static function postJson(string $url, string $body): array
    {
        $curl = curl_init($url);
        curl_setopt_array($curl, [
            CURLOPT_RETURNTRANSFER=>true,
            CURLOPT_POST=>true,
            CURLOPT_POSTFIELDS=>$body,
            CURLOPT_HTTPHEADER=>['Content-Type: application/json'],
            CURLOPT_TIMEOUT=>10,
            CURLOPT_CONNECTTIMEOUT=>5,
            CURLOPT_SSL_VERIFYPEER=>true,
            CURLOPT_SSL_VERIFYHOST=>2,
        ]);
        $response = curl_exec($curl);
        $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        $error = curl_error($curl);
        curl_close($curl);
        if ($response === false || $status !== 200) {
            Log::warning('wechat http failed | status=' . $status . ' | error=' . mb_substr($error,0,300));
            throw new \RuntimeException('微信服务暂不可用');
        }
        try {
            $decoded = json_decode($response, true, 32, JSON_THROW_ON_ERROR);
        } catch (\JsonException) {
            throw new \RuntimeException('微信服务返回格式异常');
        }
        if (!is_array($decoded)) throw new \RuntimeException('微信服务返回格式异常');
        return $decoded;
    }
}

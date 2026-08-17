<?php

declare(strict_types=1);

namespace app\service;

use think\facade\Db;

class VirtualPaymentService
{
    public static function ensureAvailable(): array
    {
        if ((string) (Db::name('setting')->where('key','payment_enabled')->value('value') ?? '0') !== '1') {
            throw new \RuntimeException('支付通道维护中，请稍后再试');
        }
        return self::configured();
    }

    public static function configured(): array
    {
        $config = ConfigService::wechat();
        $appKey = self::appKey($config);
        if (!$config['app_id'] || !$config['offer_id'] || !$appKey) {
            throw new \RuntimeException('虚拟支付参数尚未配置，请先填写 OfferID 与当前支付环境对应的 AppKey');
        }
        return $config;
    }

    public static function clientParams(array $order, array $user, array $service): array
    {
        $config = self::ensureAvailable();
        $productId = trim((string) ($service['payment_product_id'] ?? ''));
        if ($productId === '') throw new \RuntimeException('该服务尚未配置微信支付道具ID');
        $sessionKey = (string) (CryptoService::decrypt((string) ($user['session_key_cipher'] ?? ''))['value'] ?? '');
        if ($sessionKey === '') throw new \RuntimeException('微信登录态已失效，请重新进入小程序');
        $goodsPrice = (int) round(((float) $order['amount']) * 100);
        // iOS 端会由微信自动路由至 Apple 支付。Apple 支付最低金额为
        // 1 元，服务端必须阻止生成在 iOS 上必然失败的跨端商品订单。
        if ($goodsPrice < 100) throw new \RuntimeException('虚拟支付服务售价不能低于1元，请联系管理员调整服务及微信道具价格');
        $signData = [
            'offerId'=>$config['offer_id'], 'buyQuantity'=>1, 'env'=>(int) $config['pay_env'],
            'currencyType'=>'CNY', 'productId'=>$productId, 'goodsPrice'=>$goodsPrice,
            'outTradeNo'=>$order['order_no'], 'attach'=>$order['order_no'],
        ];
        $encoded = json_encode($signData, JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES);
        if ($encoded === false) throw new \RuntimeException('支付参数生成失败');
        return [
            'signData'=>$encoded,
            'paySig'=>hash_hmac('sha256', 'requestVirtualPayment&' . $encoded, self::appKey($config)),
            'signature'=>hash_hmac('sha256', $encoded, $sessionKey),
            'mode'=>'short_series_goods',
        ];
    }

    public static function appKey(array $config): string
    {
        return (int) ($config['pay_env'] ?? 0) === 1
            ? (string) ($config['sandbox_app_key'] ?? '')
            : (string) ($config['production_app_key'] ?? '');
    }
}

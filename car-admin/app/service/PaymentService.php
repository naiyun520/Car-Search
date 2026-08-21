<?php

declare(strict_types=1);

namespace app\service;

use think\facade\Db;
use think\facade\Log;

class PaymentService
{
    public static function reconcile(array $order, array $user): string
    {
        if (!in_array((string) $order['status'], ['pending_payment','payment_review'], true)) return (string) $order['status'];
        $payment = Db::name('payment')->where('order_id',$order['id'])->field('id,pay_env,last_checked_at,callback_payload')->find();
        if (!$payment) throw new \RuntimeException('订单支付记录不存在');
        $claimed = Db::name('payment')->where('id',$payment['id'])->where(function ($query) {
            $query->whereNull('last_checked_at')->whereOr('last_checked_at','<',date('Y-m-d H:i:s',strtotime('-3 seconds')));
        })->update(['last_checked_at'=>date('Y-m-d H:i:s')]);
        if (!$claimed) return (string) $order['status'];
        $payEnv = (int) $payment['pay_env'];
        $wechatOrder = WechatService::queryVirtualOrder((string) $user['openid'], (string) $order['order_no'], $payEnv);
        self::rememberWechatCheck((int) $payment['id'], $order, $wechatOrder, (string) ($payment['callback_payload'] ?? ''));
        if (!empty($wechatOrder['not_found'])) {
            if ((string) $order['status'] === 'payment_review') return 'payment_review';
            if (!empty($order['expired_at']) && strtotime((string) $order['expired_at']) <= time()) {
                $now = date('Y-m-d H:i:s');
                Db::transaction(function () use ($order,$now) {
                    Db::name('order')->where('id',$order['id'])->where('status','pending_payment')->update(['status'=>'cancelled','updated_at'=>$now]);
                    Db::name('payment')->where('order_id',$order['id'])->where('status','created')->update(['status'=>'cancelled','updated_at'=>$now]);
                });
                return 'cancelled';
            }
            return 'pending_payment';
        }
        $wechatStatus = (int) ($wechatOrder['status'] ?? 0);
        if (in_array($wechatStatus, [5,8], true)) {
            $now = date('Y-m-d H:i:s');
            Db::name('order')->where('id',$order['id'])->whereIn('status',['pending_payment','payment_review'])->update(['status'=>'refunded','updated_at'=>$now]);
            Db::name('payment')->where('order_id',$order['id'])->update(['status'=>'refunded','refund_status'=>'refunded','refund_amount'=>$order['amount'],'callback_payload'=>self::payload($wechatOrder),'refunded_at'=>$now,'updated_at'=>$now]);
            return 'refunded';
        }
        if ($wechatStatus === 6) {
            $now = date('Y-m-d H:i:s');
            Db::name('order')->where('id',$order['id'])->whereIn('status',['pending_payment','payment_review'])->update(['status'=>'cancelled','updated_at'=>$now]);
            Db::name('payment')->where('order_id',$order['id'])->update(['status'=>'cancelled','callback_payload'=>self::payload($wechatOrder),'updated_at'=>$now]);
            return 'cancelled';
        }
        if (!in_array($wechatStatus, [2,3,4], true)) return (string) $order['status'];
        // query_order 官方枚举：0=普通虚拟支付，7=苹果 iOS 支付。
        // iOS 由 requestVirtualPayment 自动路由至 Apple 支付，不能按异常订单拦截。
        $orderType = (int) ($wechatOrder['order_type'] ?? -1);
        if (!hash_equals((string) $order['order_no'], (string) ($wechatOrder['order_id'] ?? ''))
            || !in_array($orderType, [0,7], true)) {
            self::markForReview($order, $wechatOrder, '微信订单身份不匹配');
            return 'payment_review';
        }
        $expectedEnvType = $payEnv === 1 ? 2 : 1;
        if (isset($wechatOrder['env_type']) && (int) $wechatOrder['env_type'] !== $expectedEnvType) {
            self::markForReview($order, $wechatOrder, '微信订单支付环境不匹配');
            return 'payment_review';
        }
        $paidFee = (int) ($wechatOrder['paid_fee'] ?? $wechatOrder['order_fee'] ?? -1);
        $expectedFee = (int) round(((float) $order['amount']) * 100);
        if ($paidFee < 0 || $paidFee !== $expectedFee) {
            Log::error('wechat paid fee mismatch | order_no=' . $order['order_no'] . ' | expected=' . $expectedFee . ' | actual=' . $paidFee);
            self::markForReview($order, $wechatOrder, '微信订单支付金额不匹配');
            return 'payment_review';
        }
        Db::transaction(function () use ($order,$wechatOrder) {
            $now = date('Y-m-d H:i:s');
            $changed = Db::name('order')->where('id',$order['id'])->whereIn('status',['pending_payment','payment_review'])->update(['status'=>'paid','paid_at'=>$now,'updated_at'=>$now]);
            if ($changed) Db::name('payment')->where('order_id',$order['id'])->update([
                'status'=>'paid',
                // Apple 支付通常没有微信支付交易单号，保留其渠道单号用于售后核验。
                'transaction_id'=>self::firstNonEmpty($wechatOrder, ['wxpay_order_id','wx_order_id','channel_order_id']),
                'callback_payload'=>self::payload($wechatOrder),
                'paid_at'=>$now,
                'updated_at'=>$now,
            ]);
        });
        // 支付确认成功后通知管理员
        try {
            if (ConfigService::value('email_notify_payment') === '1') {
                $fullOrder = Db::name('order')->where('id', $order['id'])->find();
                if ($fullOrder) MailService::notify('💰 支付成功 - ' . $order['order_no'], MailTemplateService::paymentSuccess($fullOrder));
            }
        } catch (\Throwable) {}
        return 'paid';
    }

    private static function firstNonEmpty(array $values, array $keys): ?string
    {
        foreach ($keys as $key) {
            $value = trim((string) ($values[$key] ?? ''));
            if ($value !== '') return mb_substr($value, 0, 100);
        }
        return null;
    }

    private static function payload(array $wechatOrder): string
    {
        return mb_substr((string) json_encode($wechatOrder,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES),0,60000);
    }

    private static function rememberWechatCheck(int $paymentId, array $order, array $wechatOrder, string $previousPayload): void
    {
        $payload = self::payload($wechatOrder);
        Db::name('payment')->where('id', $paymentId)->update([
            'callback_payload'=>$payload,
            'updated_at'=>date('Y-m-d H:i:s'),
        ]);
        $previous = json_decode($previousPayload, true);
        $previousStatus = is_array($previous) ? ($previous['status'] ?? ($previous['not_found'] ?? null)) : null;
        $currentStatus = $wechatOrder['status'] ?? ($wechatOrder['not_found'] ?? null);
        if ($previousStatus !== $currentStatus) {
            Log::info('wechat payment state | order_no=' . $order['order_no']
                . ' | status=' . (isset($wechatOrder['status']) ? (string) $wechatOrder['status'] : 'not_found')
                . ' | paid_fee=' . (string) ($wechatOrder['paid_fee'] ?? ''));
        }
    }

    private static function markForReview(array $order, array $wechatOrder, string $reason): void
    {
        $now = date('Y-m-d H:i:s');
        Log::error($reason . ' | order_no=' . $order['order_no']);
        Db::transaction(function () use ($order,$wechatOrder,$now) {
            Db::name('order')->where('id',$order['id'])->whereIn('status',['pending_payment','payment_review'])->update(['status'=>'payment_review','paid_at'=>$now,'updated_at'=>$now]);
            Db::name('payment')->where('order_id',$order['id'])->update(['status'=>'review','callback_payload'=>self::payload($wechatOrder),'paid_at'=>$now,'updated_at'=>$now]);
        });
    }
}

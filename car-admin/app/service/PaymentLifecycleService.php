<?php

declare(strict_types=1);

namespace app\service;

use think\facade\Db;
use think\facade\Log;

class PaymentLifecycleService
{
    private const REFUNDABLE_ORDER_STATUSES = ['paid', 'query_failed', 'success'];

    public static function deliver(string $orderNo, bool $throwOnFailure = true): string
    {
        $row = self::paymentOrder($orderNo);
        if (!$row) throw new \RuntimeException('订单不存在');
        if ((string) $row['order_status'] !== 'success') throw new \RuntimeException('只有查询成功的订单才能通知微信发货');
        if ((string) $row['delivery_status'] === 'delivered') return 'delivered';
        if ((string) $row['delivery_status'] === 'processing'
            && strtotime((string) ($row['delivery_attempted_at'] ?? '')) > time() - 60) return 'processing';

        $now = date('Y-m-d H:i:s');
        $claimed = Db::name('payment')->where('id',$row['payment_id'])->where('delivery_status',$row['delivery_status'])->update([
            'delivery_status'=>'processing',
            'delivery_attempts'=>Db::raw('delivery_attempts + 1'),
            'delivery_attempted_at'=>$now,
            'delivery_last_error'=>null,
            'updated_at'=>$now,
        ]);
        if (!$claimed) return 'processing';
        try {
            WechatService::notifyVirtualGoodsDelivered($orderNo,(int) $row['pay_env']);
            Db::name('payment')->where('id',$row['payment_id'])->update([
                'delivery_status'=>'delivered',
                'delivered_at'=>$now,
                'delivery_last_error'=>null,
                'updated_at'=>$now,
            ]);
            return 'delivered';
        } catch (\Throwable $error) {
            try {
                $wechatOrder = WechatService::queryVirtualOrder((string) $row['openid'], $orderNo, (int) $row['pay_env']);
                if ((int) ($wechatOrder['status'] ?? 0) === 4) {
                    Db::name('payment')->where('id',$row['payment_id'])->update([
                        'delivery_status'=>'delivered',
                        'delivered_at'=>$now,
                        'delivery_last_error'=>null,
                        'updated_at'=>$now,
                    ]);
                    return 'delivered';
                }
            } catch (\Throwable) {
            }
            $message = mb_substr($error->getMessage(),0,500);
            Db::name('payment')->where('id',$row['payment_id'])->update([
                'delivery_status'=>'failed',
                'delivery_last_error'=>$message,
                'updated_at'=>date('Y-m-d H:i:s'),
            ]);
            Log::warning('wechat virtual goods delivery failed | order_no=' . $orderNo . ' | message=' . $message);
            if ($throwOnFailure) throw new \RuntimeException('微信发货确认失败，请稍后重试');
            return 'failed';
        }
    }

    public static function requestRefund(string $orderNo, string $reason): string
    {
        if (!in_array($reason, ['0','1','2','3','4','5'], true)) throw new \InvalidArgumentException('退款原因无效');
        $row = self::paymentOrder($orderNo);
        if (!$row) throw new \RuntimeException('订单不存在');
        if ((string) $row['refund_status'] === 'refunded' || (string) $row['order_status'] === 'refunded') return 'refunded';
        if (in_array((string) $row['refund_status'],['requesting','processing'],true) || (string) $row['order_status'] === 'refunding') return self::reconcileRefund($orderNo);
        if (!in_array((string) $row['order_status'], self::REFUNDABLE_ORDER_STATUSES, true)) {
            throw new \RuntimeException('当前订单状态不允许退款');
        }
        $wechatOrder = WechatService::queryVirtualOrder((string) $row['openid'],$orderNo,(int) $row['pay_env']);
        $wechatStatus = (int) ($wechatOrder['status'] ?? 0);
        if (in_array($wechatStatus,[5,8],true)) {
            self::markRefunded($row,$wechatOrder);
            return 'refunded';
        }
        if ((int) ($wechatOrder['order_type'] ?? -1) === 7) {
            throw new \RuntimeException('Apple 支付不支持开发者主动退款，请用户前往 App Store 申请退款');
        }
        if (!in_array($wechatStatus,[2,3,4],true)) throw new \RuntimeException('微信订单当前状态不允许退款');
        $refundOrderNo = trim((string) ($row['refund_order_no'] ?? ''));
        if ($refundOrderNo === '') $refundOrderNo = 'R' . date('YmdHis') . str_pad((string) $row['payment_id'],8,'0',STR_PAD_LEFT);
        $refundFee = (int) ($wechatOrder['left_fee'] ?? $wechatOrder['paid_fee'] ?? $wechatOrder['order_fee'] ?? 0);
        if ($refundFee <= 0) throw new \RuntimeException('退款金额无效');
        $refundAmount = number_format($refundFee / 100,2,'.','');
        $now = date('Y-m-d H:i:s');
        Db::transaction(function () use ($row,$refundOrderNo,$refundAmount,$reason,$now) {
            $claimed = Db::name('order')->where('id',$row['order_id'])->where('status',$row['order_status'])->update(['status'=>'refunding','updated_at'=>$now]);
            if (!$claimed) throw new \RuntimeException('订单状态已变化，请刷新后重试');
            Db::name('payment')->where('id',$row['payment_id'])->update([
                'refund_order_no'=>$refundOrderNo,
                'refund_status'=>'requesting',
                'refund_amount'=>$refundAmount,
                'refund_reason'=>$reason,
                'refund_from_status'=>$row['order_status'],
                'refund_requested_at'=>$now,
                'refund_last_error'=>null,
                'updated_at'=>$now,
            ]);
        });
        try {
            WechatService::refundVirtualOrder((string) $row['openid'], $orderNo, $refundOrderNo, $refundFee, $refundFee, $reason, (int) $row['pay_env']);
        } catch (\Throwable $error) {
            $message = mb_substr($error->getMessage(),0,500);
            if (str_starts_with($message,'微信支付接口调用失败：')) {
                self::markRefundFailed($row,$message);
                throw new \RuntimeException('微信退款申请失败：' . $message);
            }
            Db::name('payment')->where('id',$row['payment_id'])->update(['refund_last_error'=>$message,'updated_at'=>date('Y-m-d H:i:s')]);
            Log::warning('wechat virtual refund request failed | order_no=' . $orderNo . ' | refund_order_no=' . $refundOrderNo . ' | message=' . $message);
            return 'processing';
        }
        Db::name('payment')->where('id',$row['payment_id'])->update(['status'=>'refund_processing','refund_status'=>'processing','refund_last_error'=>null,'updated_at'=>$now]);
        return 'processing';
    }

    public static function reconcileRefund(string $orderNo): string
    {
        $row = self::paymentOrder($orderNo);
        if (!$row) throw new \RuntimeException('订单不存在');
        if ((string) $row['refund_status'] === 'refunded' || (string) $row['order_status'] === 'refunded') return 'refunded';
        $refundOrderNo = trim((string) ($row['refund_order_no'] ?? ''));
        if ($refundOrderNo === '') throw new \RuntimeException('订单尚未发起退款');
        try {
            $wechatOrder = WechatService::queryVirtualOrder((string) $row['openid'], $refundOrderNo, (int) $row['pay_env']);
            if (!empty($wechatOrder['not_found'])) throw new \RuntimeException('微信退款单尚未创建');
        } catch (\Throwable $refundQueryError) {
            if ((string) $row['refund_status'] === 'requesting') {
                $refundFee = (int) round(((float) ($row['refund_amount'] ?: $row['amount'])) * 100);
                try {
                    $payOrder = WechatService::queryVirtualOrder((string) $row['openid'],$orderNo,(int) $row['pay_env']);
                    $leftFee = (int) ($payOrder['left_fee'] ?? 0);
                    if ($leftFee <= 0 || $refundFee > $leftFee) throw new \RuntimeException('微信订单剩余可退金额不足');
                    WechatService::refundVirtualOrder((string) $row['openid'],$orderNo,$refundOrderNo,$leftFee,$refundFee,(string) ($row['refund_reason'] ?: '2'),(int) $row['pay_env']);
                    Db::name('payment')->where('id',$row['payment_id'])->update(['status'=>'refund_processing','refund_status'=>'processing','refund_last_error'=>null,'updated_at'=>date('Y-m-d H:i:s')]);
                } catch (\Throwable $submitError) {
                    $message = mb_substr($submitError->getMessage(),0,500);
                    if (str_starts_with($message,'微信支付接口调用失败：')) {
                        self::markRefundFailed($row,$message);
                        return 'failed';
                    }
                    Db::name('payment')->where('id',$row['payment_id'])->update(['refund_last_error'=>$message,'updated_at'=>date('Y-m-d H:i:s')]);
                }
                return 'processing';
            }
            $wechatOrder = WechatService::queryVirtualOrder((string) $row['openid'], $orderNo, (int) $row['pay_env']);
        }
        $wechatStatus = (int) ($wechatOrder['status'] ?? 0);
        if (in_array($wechatStatus,[5,8],true)) {
            self::markRefunded($row,$wechatOrder);
            return 'refunded';
        }
        if ($wechatStatus === 7) {
            $restoreStatus = in_array((string) $row['refund_from_status'],self::REFUNDABLE_ORDER_STATUSES,true) ? (string) $row['refund_from_status'] : 'query_failed';
            $now = date('Y-m-d H:i:s');
            Db::transaction(function () use ($row,$wechatOrder,$restoreStatus,$now) {
                Db::name('order')->where('id',$row['order_id'])->where('status','refunding')->update(['status'=>$restoreStatus,'updated_at'=>$now]);
                Db::name('payment')->where('id',$row['payment_id'])->update([
                    'status'=>'paid',
                    'refund_status'=>'failed',
                    'refund_payload'=>self::payload($wechatOrder),
                    'refund_last_error'=>'微信退款处理失败',
                    'updated_at'=>$now,
                ]);
            });
            return 'failed';
        }
        return 'processing';
    }

    public static function reconcilePending(int $limit = 50): array
    {
        $limit = max(1,min(200,$limit));
        $staleAt = date('Y-m-d H:i:s',strtotime('-5 minutes'));
        $paymentRows = Db::name('order')->alias('o')->join('user u','u.id=o.user_id')->join('payment p','p.order_id=o.id')->whereIn('o.status',['pending_payment','payment_review'])
            ->where('o.created_at','<',date('Y-m-d H:i:s',strtotime('-20 seconds')))
            ->where('o.created_at','>=',date('Y-m-d H:i:s',strtotime('-2 days')))
            ->field('o.*,u.openid')->orderRaw('COALESCE(p.last_checked_at,\'1970-01-01 00:00:00\') ASC')->limit($limit)->select()->toArray();
        $paid = 0;
        $paidOrderNos = [];
        foreach ($paymentRows as $order) {
            try { if (PaymentService::reconcile($order,$order) === 'paid') { $paid++; $paidOrderNos[] = $order['order_no']; } }
            catch (\Throwable $error) { Log::warning('wechat payment reconcile failed | order_no=' . $order['order_no'] . ' | message=' . mb_substr($error->getMessage(),0,500)); }
        }
        $queried = 0;
        foreach ($paidOrderNos as $orderNo) {
            try { if (QueryRunnerService::runForOrderNo((string) $orderNo)['status'] === 'success') $queried++; }
            catch (\Throwable $error) { Log::warning('auto query after reconcile failed | order_no=' . $orderNo . ' | message=' . mb_substr($error->getMessage(),0,500)); }
        }

        $staleQuerying = Db::name('order')->where('status','querying')->where('updated_at','<',$staleAt)->field('order_no')->limit($limit)->select()->toArray();
        $recovered = 0;
        foreach ($staleQuerying as $item) {
            try {
                $claimed = Db::name('order')->where('order_no',$item['order_no'])->where('status','querying')->where('updated_at','<',$staleAt)->update(['status'=>'paid','updated_at'=>date('Y-m-d H:i:s')]);
                if (!$claimed) continue;
                if (QueryRunnerService::runForOrderNo((string) $item['order_no'])['status'] === 'success') $recovered++;
            } catch (\Throwable $error) { Log::warning('stale querying order recovery failed | order_no=' . $item['order_no'] . ' | message=' . mb_substr($error->getMessage(),0,500)); }
        }

        $deliveryRows = Db::name('payment')->alias('p')->join('order o','o.id=p.order_id')->where('o.status','success')
            ->whereRaw("(p.delivery_status IN ('pending','failed') OR (p.delivery_status='processing' AND p.delivery_attempted_at < ?))",[$staleAt])
            ->field('p.order_no')->limit($limit)->select()->toArray();
        $delivered = 0;
        foreach ($deliveryRows as $item) if (self::deliver((string) $item['order_no'],false) === 'delivered') $delivered++;

        $refundRows = Db::name('payment')->whereIn('refund_status',['requesting','processing'])->field('order_no')->limit($limit)->select()->toArray();
        $refunded = 0;
        foreach ($refundRows as $item) {
            try { if (self::reconcileRefund((string) $item['order_no']) === 'refunded') $refunded++; }
            catch (\Throwable $error) { Log::warning('wechat refund reconcile failed | order_no=' . $item['order_no'] . ' | message=' . mb_substr($error->getMessage(),0,500)); }
        }
        return ['payment_checked'=>count($paymentRows),'paid'=>$paid,'queried'=>$queried,'recovered'=>$recovered,'delivery_checked'=>count($deliveryRows),'delivered'=>$delivered,'refund_checked'=>count($refundRows),'refunded'=>$refunded];
    }

    private static function paymentOrder(string $orderNo): ?array
    {
        return Db::name('order')->alias('o')
            ->join('payment p','p.order_id=o.id')
            ->join('user u','u.id=o.user_id')
            ->where('o.order_no',$orderNo)
            ->field('o.id order_id,o.status order_status,o.amount,o.user_id,p.id payment_id,p.status payment_status,p.pay_env,p.delivery_status,p.delivery_attempts,p.delivery_attempted_at,p.refund_order_no,p.refund_status,p.refund_amount,p.refund_reason,p.refund_from_status,p.refund_requested_at,u.openid')
            ->find();
    }

    private static function markRefundFailed(array $row, string $message): void
    {
        $restoreStatus = in_array((string) $row['refund_from_status'],self::REFUNDABLE_ORDER_STATUSES,true)
            ? (string) $row['refund_from_status']
            : (in_array((string) $row['order_status'],self::REFUNDABLE_ORDER_STATUSES,true) ? (string) $row['order_status'] : 'query_failed');
        $now = date('Y-m-d H:i:s');
        Db::transaction(function () use ($row,$restoreStatus,$message,$now) {
            Db::name('order')->where('id',$row['order_id'])->where('status','refunding')->update(['status'=>$restoreStatus,'updated_at'=>$now]);
            Db::name('payment')->where('id',$row['payment_id'])->update(['status'=>'paid','refund_status'=>'failed','refund_last_error'=>$message,'updated_at'=>$now]);
        });
    }

    private static function markRefunded(array $row, array $wechatOrder): void
    {
        $now = date('Y-m-d H:i:s');
        $refundAmount = !empty($row['refund_amount'])
            ? $row['refund_amount']
            : number_format(((int) ($wechatOrder['refund_fee'] ?? round(((float) $row['amount']) * 100))) / 100,2,'.','');
        Db::transaction(function () use ($row,$wechatOrder,$refundAmount,$now) {
            Db::name('order')->where('id',$row['order_id'])->update(['status'=>'refunded','updated_at'=>$now]);
            Db::name('payment')->where('id',$row['payment_id'])->update([
                'status'=>'refunded',
                'refund_status'=>'refunded',
                'refund_amount'=>$refundAmount,
                'refund_payload'=>self::payload($wechatOrder),
                'refunded_at'=>$now,
                'refund_last_error'=>null,
                'updated_at'=>$now,
            ]);
        });
    }

    private static function payload(array $data): string
    {
        return mb_substr((string) json_encode($data,JSON_UNESCAPED_UNICODE|JSON_UNESCAPED_SLASHES),0,60000);
    }
}

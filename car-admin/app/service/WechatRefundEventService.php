<?php

declare(strict_types=1);

namespace app\service;

use think\facade\Db;
use think\facade\Log;

final class WechatRefundEventService
{
    private const IOS_INQUIRY_EVENT = 'xpay_subscribe_ios_refund_query_notify';
    private const REFUND_NOTIFY_EVENT = 'xpay_refund_notify';
    private const GOODS_DELIVER_EVENT = 'xpay_goods_deliver_notify';

    public static function handle(array $event): array
    {
        $eventType = strtolower(trim((string) self::field($event, ['Event', 'event'])));
        if ($eventType === self::IOS_INQUIRY_EVENT) return self::handleIosInquiry($event);
        if ($eventType === self::REFUND_NOTIFY_EVENT) return self::handleRefundNotify($event);
        if ($eventType === self::GOODS_DELIVER_EVENT) return self::handleGoodsDeliverNotify($event);
        Log::info('wechat message event acknowledged | event=' . mb_substr($eventType, 0, 64));
        return ['ErrCode'=>0, 'ErrMsg'=>'success'];
    }

    private static function handleGoodsDeliverNotify(array $event): array
    {
        $orderNo = trim((string) self::field($event, ['OutTradeNo', 'out_trade_no']));
        $payInfo = self::field($event, ['WeChatPayInfo', 'wechat_pay_info'], []);
        if (!is_array($payInfo)) $payInfo = [];
        $transactionId = trim((string) self::field($payInfo, ['TransactionId', 'transaction_id']));
        $merchantOrderNo = trim((string) self::field($payInfo, ['MchOrderNo', 'mch_order_no']));
        $eventKey = self::eventKey(self::GOODS_DELIVER_EVENT, [$orderNo, $transactionId, $merchantOrderNo]);
        $existing = Db::name('wechat_event')->where('event_key', $eventKey)->find();
        if ($existing && (string) $existing['process_status'] === 'processed') return ['ErrCode'=>0, 'ErrMsg'=>'success'];
        self::ensureEvent($eventKey, self::GOODS_DELIVER_EVENT, $event, $orderNo ?: null);
        if ($orderNo === '') {
            self::failEvent($eventKey, '发货通知缺少业务订单号');
            throw new \RuntimeException('发货通知缺少业务订单号');
        }
        $payment = Db::name('payment')->where('order_no', $orderNo)->find();
        if (!$payment) {
            self::failEvent($eventKey, '发货通知无法匹配本地订单');
            throw new \RuntimeException('发货通知无法匹配本地订单');
        }
        $update = ['updated_at'=>date('Y-m-d H:i:s')];
        if ($transactionId !== '') {
            $update['transaction_id'] = mb_substr($transactionId, 0, 100);
            $update['wechat_pay_transaction_id'] = mb_substr($transactionId, 0, 100);
        }
        if ($merchantOrderNo !== '') $update['channel_order_id'] = mb_substr($merchantOrderNo, 0, 100);
        Db::transaction(function () use ($payment, $eventKey, $orderNo, $update) {
            Db::name('payment')->where('id', $payment['id'])->update($update);
            Db::name('wechat_event')->where('event_key', $eventKey)->update([
                'order_no'=>$orderNo,
                'response_cipher'=>CryptoService::encrypt(['ErrCode'=>0, 'ErrMsg'=>'success']),
                'process_status'=>'processed',
                'last_error'=>null,
                'updated_at'=>date('Y-m-d H:i:s'),
            ]);
        });
        return ['ErrCode'=>0, 'ErrMsg'=>'success'];
    }

    private static function handleIosInquiry(array $event): array
    {
        $payOrderId = trim((string) self::field($event, ['pay_order_id', 'PayOrderId']));
        $channelBill = trim((string) self::field($event, ['channel_bill', 'ChannelBill']));
        $eventKey = self::eventKey(self::IOS_INQUIRY_EVENT, [
            $payOrderId,
            $channelBill,
            (string) self::field($event, ['refund_time', 'RefundTime']),
        ]);
        $stored = self::storedResponse($eventKey);
        if ($stored !== null) return $stored;

        $row = self::findPaymentOrder($payOrderId, $channelBill, '');
        $provideStatus = (int) self::field($event, ['provide_status', 'ProvideStatus'], -1);
        $productId = trim((string) self::field($event, ['product_id', 'ProductId']));
        $productMatches = self::productMatches($row, $productId);
        $delivered = $row
            && (string) $row['order_status'] === 'success'
            && (string) $row['delivery_status'] === 'delivered'
            && $provideStatus === 1
            && $productMatches;

        if ($delivered) {
            $response = [
                'result_code'=>1,
                'result_info'=>'订单已完成服务交付，建议不予退款',
                'evidence'=>'业务订单 ' . $row['order_no'] . ' 已于 ' . ($row['delivered_at'] ?: $row['updated_at']) . ' 完成查询并向微信确认发货。',
            ];
        } else {
            $reason = !$row
                ? '本地未找到可核验的已履约记录'
                : (!$productMatches ? '退款商品与本地订单商品无法完成一致性核验' : '本地记录未同时满足查询成功和微信确认发货');
            $response = [
                'result_code'=>0,
                'result_info'=>'未能确认服务已经完整交付，建议退款',
                'evidence'=>$reason . ($row ? '，业务订单 ' . $row['order_no'] : '') . '。',
            ];
        }

        $orderNo = $row ? (string) $row['order_no'] : null;
        self::storeInquiry($eventKey, $event, $response, $orderNo);
        if ($row && (string) $row['refund_status'] !== 'refunded') {
            $requestedAt = self::timestampDate(self::field($event, ['refund_time', 'RefundTime'])) ?: date('Y-m-d H:i:s');
            Db::name('payment')->where('id', $row['payment_id'])->update([
                'refund_source'=>'apple',
                'refund_status'=>'apple_review',
                'refund_reason'=>'apple',
                'refund_requested_at'=>$row['refund_requested_at'] ?: $requestedAt,
                'refund_last_error'=>null,
                'updated_at'=>date('Y-m-d H:i:s'),
            ]);
        }
        Log::info('apple refund inquiry answered | order_no=' . ($orderNo ?: 'unmatched') . ' | result_code=' . $response['result_code']);
        return $response;
    }

    private static function handleRefundNotify(array $event): array
    {
        $wxRefundId = trim((string) self::field($event, ['WxRefundId', 'wx_refund_id']));
        $mchRefundId = trim((string) self::field($event, ['MchRefundId', 'mch_refund_id']));
        $eventKey = self::eventKey(self::REFUND_NOTIFY_EVENT, [
            $wxRefundId,
            $mchRefundId,
            (string) self::field($event, ['RetCode', 'ret_code']),
            (string) self::field($event, ['RefundSuccTimestamp', 'refund_succ_timestamp']),
        ]);
        $existing = Db::name('wechat_event')->where('event_key', $eventKey)->find();
        if ($existing && (string) $existing['process_status'] === 'processed') return ['ErrCode'=>0, 'ErrMsg'=>'success'];
        self::ensureEvent($eventKey, self::REFUND_NOTIFY_EVENT, $event, null);

        $mchOrderId = trim((string) self::field($event, ['MchOrderId', 'mch_order_id']));
        $wxOrderId = trim((string) self::field($event, ['WxOrderId', 'wx_order_id']));
        $transactionId = trim((string) self::field($event, ['TransactionId', 'transaction_id']));
        $row = self::findPaymentOrder($mchOrderId, $wxOrderId, $transactionId);
        if (!$row) {
            self::failEvent($eventKey, '退款通知无法匹配本地订单');
            throw new \RuntimeException('退款通知无法匹配本地订单');
        }
        $openid = trim((string) self::field($event, ['OpenId', 'openid']));
        if ($openid !== '' && (string) $row['openid'] !== '' && !hash_equals((string) $row['openid'], $openid)) {
            self::failEvent($eventKey, '退款通知用户标识不匹配');
            throw new \RuntimeException('退款通知用户标识不匹配');
        }

        $retCode = (int) self::field($event, ['RetCode', 'ret_code'], -1);
        $now = date('Y-m-d H:i:s');
        $refundNo = mb_substr($mchRefundId !== '' ? $mchRefundId : $wxRefundId, 0, 100);
        $refundFee = (int) self::field($event, ['RefundFee', 'refund_fee'], 0);
        $refundAmount = number_format(max(0, $refundFee) / 100, 2, '.', '');
        $source = (int) ($row['wechat_order_type'] ?? -1) === 7 ? 'apple' : 'wechat_notify';
        $payload = mb_substr((string) json_encode($event, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), 0, 60000);

        Db::transaction(function () use ($row, $eventKey, $event, $retCode, $refundNo, $refundAmount, $source, $payload, $mchOrderId, $wxOrderId, $now) {
            if ($retCode === 0) {
                Db::name('order')->where('id', $row['order_id'])->update(['status'=>'refunded', 'updated_at'=>$now]);
                $paymentUpdate = [
                    'status'=>'refunded',
                    'refund_status'=>'refunded',
                    'refund_source'=>$source,
                    'refund_amount'=>$refundAmount,
                    'refund_payload'=>$payload,
                    'refund_last_error'=>null,
                    'refunded_at'=>self::timestampDate(self::field($event, ['RefundSuccTimestamp', 'refund_succ_timestamp'])) ?: $now,
                    'updated_at'=>$now,
                ];
                if ($refundNo !== '') $paymentUpdate['refund_order_no'] = $refundNo;
                if ($wxOrderId !== '') $paymentUpdate['wechat_order_id'] = mb_substr($wxOrderId, 0, 100);
                if ($mchOrderId !== '' && $mchOrderId !== (string) $row['order_no']) $paymentUpdate['channel_order_id'] = mb_substr($mchOrderId, 0, 100);
                Db::name('payment')->where('id', $row['payment_id'])->update($paymentUpdate);
            } else {
                $restoreStatus = (string) $row['order_status'] === 'refunding'
                    ? ((string) ($row['refund_from_status'] ?: 'query_failed'))
                    : (string) $row['order_status'];
                if ((string) $row['order_status'] === 'refunding') {
                    Db::name('order')->where('id', $row['order_id'])->update(['status'=>$restoreStatus, 'updated_at'=>$now]);
                }
                Db::name('payment')->where('id', $row['payment_id'])->update([
                    'status'=>'paid',
                    'refund_status'=>'failed',
                    'refund_source'=>$source,
                    'refund_payload'=>$payload,
                    'refund_last_error'=>mb_substr((string) self::field($event, ['RetMsg', 'ret_msg'], '退款未获批准'), 0, 500),
                    'updated_at'=>$now,
                ]);
            }
            Db::name('wechat_event')->where('event_key', $eventKey)->update([
                'order_no'=>$row['order_no'],
                'response_cipher'=>CryptoService::encrypt(['ErrCode'=>0, 'ErrMsg'=>'success']),
                'process_status'=>'processed',
                'last_error'=>null,
                'updated_at'=>$now,
            ]);
        });
        Log::info('wechat refund notify processed | order_no=' . $row['order_no'] . ' | ret_code=' . $retCode . ' | source=' . $source);
        return ['ErrCode'=>0, 'ErrMsg'=>'success'];
    }

    private static function findPaymentOrder(string $merchantOrderId, string $wechatOrderId, string $transactionId): ?array
    {
        $identifiers = array_values(array_unique(array_filter([$merchantOrderId, $wechatOrderId, $transactionId], static fn($value) => $value !== '')));
        if (!$identifiers) return null;
        return Db::name('order')->alias('o')
            ->join('payment p', 'p.order_id=o.id')
            ->join('user u', 'u.id=o.user_id')
            ->where(function ($query) use ($identifiers) {
                foreach ($identifiers as $identifier) {
                    $query->whereOr('o.order_no', $identifier)
                        ->whereOr('p.order_no', $identifier)
                        ->whereOr('p.transaction_id', $identifier)
                        ->whereOr('p.wechat_order_id', $identifier)
                        ->whereOr('p.wechat_pay_transaction_id', $identifier)
                        ->whereOr('p.channel_order_id', $identifier);
                }
            })
            ->field('o.id order_id,o.order_no,o.status order_status,o.service_snapshot_cipher,o.updated_at,p.id payment_id,p.delivery_status,p.delivered_at,p.refund_status,p.refund_from_status,p.refund_requested_at,p.wechat_order_type,u.openid')
            ->find();
    }

    private static function productMatches(?array $row, string $productId): bool
    {
        if (!$row || $productId === '') return $row !== null;
        try {
            $snapshot = CryptoService::decrypt((string) ($row['service_snapshot_cipher'] ?? ''));
            return isset($snapshot['payment_product_id']) && hash_equals((string) $snapshot['payment_product_id'], $productId);
        } catch (\Throwable) {
            return false;
        }
    }

    private static function storeInquiry(string $eventKey, array $event, array $response, ?string $orderNo): void
    {
        self::ensureEvent($eventKey, self::IOS_INQUIRY_EVENT, $event, $orderNo);
        Db::name('wechat_event')->where('event_key', $eventKey)->update([
            'order_no'=>$orderNo,
            'response_cipher'=>CryptoService::encrypt($response),
            'process_status'=>'processed',
            'last_error'=>null,
            'updated_at'=>date('Y-m-d H:i:s'),
        ]);
    }

    private static function ensureEvent(string $eventKey, string $eventType, array $event, ?string $orderNo): void
    {
        if (Db::name('wechat_event')->where('event_key', $eventKey)->find()) return;
        $now = date('Y-m-d H:i:s');
        try {
            Db::name('wechat_event')->insert([
                'event_key'=>$eventKey,
                'event_type'=>$eventType,
                'order_no'=>$orderNo,
                'request_cipher'=>CryptoService::encrypt($event),
                'process_status'=>'received',
                'created_at'=>$now,
                'updated_at'=>$now,
            ]);
        } catch (\Throwable $error) {
            if (!Db::name('wechat_event')->where('event_key', $eventKey)->find()) throw $error;
        }
    }

    private static function storedResponse(string $eventKey): ?array
    {
        $cipher = Db::name('wechat_event')->where('event_key', $eventKey)->value('response_cipher');
        if (!is_string($cipher) || $cipher === '') return null;
        $response = CryptoService::decrypt($cipher);
        return $response ?: null;
    }

    private static function failEvent(string $eventKey, string $message): void
    {
        Db::name('wechat_event')->where('event_key', $eventKey)->update([
            'process_status'=>'failed',
            'last_error'=>mb_substr($message, 0, 500),
            'updated_at'=>date('Y-m-d H:i:s'),
        ]);
        Log::warning('wechat refund event failed | event_key=' . $eventKey . ' | message=' . $message);
    }

    private static function eventKey(string $eventType, array $parts): string
    {
        return hash('sha256', $eventType . '|' . implode('|', array_map('strval', $parts)));
    }

    private static function field(array $event, array $names, mixed $default = ''): mixed
    {
        foreach ($names as $name) if (array_key_exists($name, $event)) return $event[$name];
        $lower = array_change_key_case($event, CASE_LOWER);
        foreach ($names as $name) {
            $key = strtolower($name);
            if (array_key_exists($key, $lower)) return $lower[$key];
        }
        return $default;
    }

    private static function timestampDate(mixed $value): ?string
    {
        if (!is_numeric($value)) return null;
        $timestamp = (int) $value;
        if ($timestamp > 9999999999) $timestamp = (int) floor($timestamp / 1000);
        if ($timestamp < 946684800 || $timestamp > time() + 86400) return null;
        return date('Y-m-d H:i:s', $timestamp);
    }
}


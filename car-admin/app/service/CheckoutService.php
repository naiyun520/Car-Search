<?php

declare(strict_types=1);

namespace app\service;

use think\facade\Db;
use think\facade\Log;

final class CheckoutService
{
    public const CLIENT_PROTOCOL = '20260815.2';
    public static function create(array $user, string $serviceCode, string $requestKey, array $payload): array
    {
        // 幂等检查必须先于业务参数校验：首个请求已到达但响应丢失时，即使页面重载丢失表单，也应恢复原订单。
        $existing = Db::name('order')->where('user_id', $user['id'])->where('request_key', $requestKey)->find();
        if ($existing) return ['order'=>self::safeOrder($existing), 'payment'=>null, 'reused'=>true];
        if (empty($payload['accepted'])) throw new \InvalidArgumentException('请先阅读并同意授权协议与免责声明');
        $service = Db::name('service')->where('code', $serviceCode)->where('status', 1)->find();
        if (!$service) throw new \InvalidArgumentException('该服务当前不可用，请返回首页刷新服务目录');
        VirtualPaymentService::ensureAvailable();
        $input = InputValidator::validate(json_decode((string) $service['input_schema'], true) ?: [], (array) ($payload['input'] ?? []));

        $now = date('Y-m-d H:i:s');
        $orderNo = self::newOrderNo((int) $user['id']);
        $summary = implode(' / ', array_map(static fn($value) => self::mask((string) $value), $input));
        $orderData = [
            'order_no'=>$orderNo,
            'request_key'=>$requestKey,
            'user_id'=>$user['id'],
            'service_id'=>$service['id'],
            'service_name'=>$service['name'],
            'amount'=>$service['sale_price'],
            'cost_amount'=>$service['cost_price'],
            'status'=>'pending_payment',
            'input_cipher'=>CryptoService::encrypt($input),
            'service_snapshot_cipher'=>CryptoService::encrypt($service),
            'input_summary'=>mb_substr($summary, 0, 100),
            'query_attempts'=>0,
            'expired_at'=>date('Y-m-d H:i:s', strtotime('+30 minutes')),
            'created_at'=>$now,
            'updated_at'=>$now,
        ];
        $payment = VirtualPaymentService::clientParams($orderData, $user, $service);
        $payEnv = (int) ((json_decode($payment['signData'], true) ?: [])['env'] ?? 0);

        try {
            $orderId = Db::transaction(function () use ($orderData, $orderNo, $service, $payEnv, $now) {
                $id = Db::name('order')->insertGetId($orderData);
                Db::name('payment')->insert([
                    'order_id'=>$id,
                    'order_no'=>$orderNo,
                    'amount'=>$service['sale_price'],
                    'pay_env'=>$payEnv,
                    'status'=>'created',
                    'delivery_status'=>'pending',
                    'refund_status'=>'none',
                    'created_at'=>$now,
                    'updated_at'=>$now,
                ]);
                return $id;
            });
        } catch (\Throwable $error) {
            $existing = Db::name('order')->where('user_id', $user['id'])->where('request_key', $requestKey)->find();
            if ($existing) return ['order'=>self::safeOrder($existing), 'payment'=>null, 'reused'=>true];
            throw $error;
        }

        $order = Db::name('order')->where('id', $orderId)->find();
        Log::info('checkout created | order_no=' . $orderNo . ' | service=' . $serviceCode . ' | user_id=' . $user['id']);
        return ['order'=>self::safeOrder($order), 'payment'=>$payment, 'reused'=>false];
    }

    public static function status(array $user, string $orderNo, bool $reconcilePayment = true): array
    {
        $order = self::ownedOrder($user, $orderNo);
        if ($reconcilePayment && in_array((string) $order['status'], ['pending_payment','payment_review'], true)) {
            PaymentService::reconcile($order, $user);
            $order = self::ownedOrder($user, $orderNo);
        }
        return self::detail($order);
    }

    public static function confirm(array $user, string $orderNo): array
    {
        return self::status($user, $orderNo, true);
    }

    public static function payment(array $user, string $orderNo): array
    {
        $order = self::ownedOrder($user, $orderNo);
        if ($order['status'] !== 'pending_payment') return ['order'=>self::detail($order), 'payment'=>null];
        PaymentService::reconcile($order, $user);
        $order = self::ownedOrder($user, $orderNo);
        if ($order['status'] !== 'pending_payment') return ['order'=>self::detail($order), 'payment'=>null];
        if (strtotime((string) $order['expired_at']) <= time()) {
            Db::name('order')->where('id', $order['id'])->where('status', 'pending_payment')->update(['status'=>'cancelled','updated_at'=>date('Y-m-d H:i:s')]);
            $order['status'] = 'cancelled';
            return ['order'=>self::detail($order), 'payment'=>null];
        }
        try {
            $service = CryptoService::decrypt((string) ($order['service_snapshot_cipher'] ?? ''));
        } catch (\Throwable) {
            throw new \RuntimeException('订单支付配置快照不可用，请联系客服处理');
        }
        if (!$service || empty($service['payment_product_id'])) throw new \RuntimeException('订单支付配置快照不可用');
        return ['order'=>self::safeOrder($order), 'payment'=>VirtualPaymentService::clientParams($order, $user, $service)];
    }

    public static function query(array $user, string $orderNo): array
    {
        $order = self::ownedOrder($user, $orderNo);
        // A provider failure is a terminal user-facing state. Only an
        // authenticated administrator may explicitly start another billable
        // provider request after checking the failure reason.
        if ($order['status'] === 'paid') {
            QueryRunnerService::run($order);
        }
        return self::detail(self::ownedOrder($user, $orderNo));
    }

    public static function recoverable(array $user): ?array
    {
        $order = Db::name('order')->where('user_id', $user['id'])
            ->whereNotNull('paid_at')
            ->whereIn('status', ['paid','querying','query_failed','success','payment_review'])
            ->where('created_at', '>=', date('Y-m-d H:i:s', strtotime('-24 hours')))
            ->order('id', 'desc')->find();
        return $order ? self::status($user, (string) $order['order_no'], true) : null;
    }

    public static function safeOrder(array $order): array
    {
        return [
            'order_no'=>(string) $order['order_no'],
            'service_name'=>(string) $order['service_name'],
            'status'=>(string) $order['status'],
            'paid_at'=>$order['paid_at'] ?? null,
            'queried_at'=>$order['queried_at'] ?? null,
            'created_at'=>$order['created_at'] ?? null,
        ];
    }

    private static function detail(array $order): array
    {
        $safe = self::safeOrder($order);
        if ($order['status'] === 'success' && !empty($order['result_cipher'])) {
            $safe['result'] = CryptoService::decrypt((string) $order['result_cipher'])['items'] ?? [];
            try { PaymentLifecycleService::deliver((string) $order['order_no'], false); } catch (\Throwable) {}
        }
        if ($order['status'] === 'query_failed') $safe['message'] = '查询未成功，请联系客服处理';
        if ($order['status'] === 'payment_review') $safe['message'] = '支付信息需要人工核对，请勿重复支付并联系客服';
        return $safe;
    }

    private static function ownedOrder(array $user, string $orderNo): array
    {
        $order = Db::name('order')->where('order_no', $orderNo)->where('user_id', $user['id'])->find();
        if (!$order) throw new \InvalidArgumentException('订单不存在或无权访问');
        return $order;
    }

    private static function newOrderNo(int $userId): string
    {
        return date('ymdHis') . str_pad((string) ($userId % 1000000), 6, '0', STR_PAD_LEFT) . strtoupper(bin2hex(random_bytes(4)));
    }

    private static function mask(string $value): string
    {
        $length = mb_strlen($value);
        if ($length <= 2) return str_repeat('*', $length);
        return mb_substr($value, 0, 1) . str_repeat('*', min(6, $length - 2)) . mb_substr($value, -1);
    }
}

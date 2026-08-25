<?php

declare(strict_types=1);

namespace app\service;

use think\facade\Db;
use think\facade\Log;

/**
 * 供应商查询执行唯一入口。
 * 所有查询路径（支付确认、客户端发起、定时任务补偿、后台重试）都必须收敛到这里，
 * 通过 paid→querying 原子抢占保证同一订单最多只有一个执行者，杜绝并发重复调用供应商。
 */
class QueryRunnerService
{
    public static function runForOrderNo(string $orderNo): array
    {
        $order = Db::name('order')->where('order_no', $orderNo)->find();
        if (!$order) throw new \RuntimeException('订单不存在');
        return self::run($order);
    }

    /** 幂等执行：success/query_failed 为终态直接返回；querying 返回进行中；paid 抢占后执行查询 */
    public static function run(array $order): array
    {
        $status = (string) $order['status'];
        if ($status === 'success') return ['status' => 'success'];
        if ($status === 'query_failed') return ['status' => 'query_failed'];
        if ($status === 'querying') return ['status' => 'querying'];
        if ($status !== 'paid') return ['status' => $status];

        $now = date('Y-m-d H:i:s');
        $claimed = Db::name('order')->where('id', $order['id'])->where('status', 'paid')
            ->update(['status' => 'querying', 'query_attempts' => Db::raw('query_attempts + 1'), 'query_last_error'=>null, 'updated_at' => $now]);
        if (!$claimed) {
            $fresh = Db::name('order')->where('id', $order['id'])->find();
            return self::run($fresh ?: $order);
        }

        $service = self::orderService($order);
        if (!$service) {
            Db::name('order')->where('id', $order['id'])->update(['status' => 'query_failed', 'provider_code' => 'error', 'query_last_error'=>'订单服务快照不可用', 'queried_at' => $now, 'updated_at' => $now]);
            return ['status' => 'query_failed'];
        }
        try {
            $response = ProviderService::query($service, CryptoService::decrypt((string) $order['input_cipher']));
            $finishedAt = date('Y-m-d H:i:s');
            Db::name('order')->where('id', $order['id'])->update([
                'status' => 'success',
                'provider_code' => mb_substr((string) ($response['code'] ?? '200'), 0, 20),
                'provider_request_id' => mb_substr((string) ($response['request_id'] ?? ''), 0, 100) ?: null,
                'query_last_error' => null,
                'result_cipher' => CryptoService::encrypt(['items' => $response['data']]),
                'queried_at' => $finishedAt,
                'updated_at' => $finishedAt,
            ]);
            try {
                PaymentLifecycleService::deliver((string) $order['order_no'], false);
            } catch (\Throwable $error) {
                Log::warning('payment delivery scheduling failed | order_no=' . $order['order_no'] . ' | message=' . mb_substr($error->getMessage(), 0, 500));
            }
            // 查询成功后通知管理员
            try {
                if (ConfigService::value('email_notify_query_success') === '1') {
                    $notifyOrder = Db::name('order')->where('id', $order['id'])->find();
                    if ($notifyOrder) MailService::notify('✅ 查询成功 - ' . $order['order_no'], MailTemplateService::querySuccess($notifyOrder));
                }
            } catch (\Throwable) {}
            return ['status' => 'success'];
        } catch (\Throwable $error) {
            $finishedAt = date('Y-m-d H:i:s');
            $message = mb_substr($error->getMessage(), 0, 500);
            $providerCode = $error instanceof ProviderQueryException ? $error->providerCode : 'error';
            $providerRequestId = $error instanceof ProviderQueryException ? $error->providerRequestId : '';
            Log::error('provider query failed | order_no=' . $order['order_no'] . ' | message=' . $message);
            Db::name('order')->where('id', $order['id'])->update([
                'status' => 'query_failed',
                'provider_code' => mb_substr($providerCode, 0, 20),
                'provider_request_id' => $providerRequestId !== '' ? mb_substr($providerRequestId, 0, 100) : null,
                'query_last_error'=>$message,
                'queried_at' => $finishedAt,
                'updated_at' => $finishedAt,
            ]);
            // 查询失败后通知管理员
            try {
                if (ConfigService::value('email_notify_query_failed') === '1') {
                    $notifyOrder = Db::name('order')->where('id', $order['id'])->find();
                    if ($notifyOrder) MailService::notify('⚠️ 查询异常 - ' . $order['order_no'], MailTemplateService::queryFailed($notifyOrder, $message));
                }
            } catch (\Throwable) {}
            return ['status' => 'query_failed'];
        }
    }

    /** 仅供后台人工操作的重试入口。 */
    public static function retryFromAdmin(string $orderNo): array
    {
        $order = Db::name('order')->where('order_no', $orderNo)->find();
        if (!$order) throw new \RuntimeException('订单不存在');
        if ((string) $order['status'] !== 'query_failed') return self::run($order);
        self::refreshServiceSnapshotForAdmin($order);
        $claimed = Db::name('order')->where('id', $order['id'])->where('status', 'query_failed')
            ->update(['status' => 'paid', 'updated_at' => date('Y-m-d H:i:s')]);
        if (!$claimed) {
            $fresh = Db::name('order')->where('id', $order['id'])->find();
            return self::run($fresh ?: $order);
        }
        $fresh = Db::name('order')->where('id', $order['id'])->find() ?: $order;
        return self::run($fresh);
    }

    /**
     * An administrator retry is an explicit decision to use the currently
     * configured provider connection. Service identity and input names must
     * still match the paid order, preventing an old order from being sent to
     * a different product after catalog edits.
     */
    private static function refreshServiceSnapshotForAdmin(array $order): void
    {
        $current = Db::name('service')->where('id', $order['service_id'])->find();
        if (!$current) throw new \RuntimeException('原订单对应的服务配置不存在');

        $snapshot = CryptoService::decrypt((string) ($order['service_snapshot_cipher'] ?? ''));
        if (!$snapshot || (string) ($snapshot['code'] ?? '') !== (string) ($current['code'] ?? '')) {
            throw new \RuntimeException('订单服务快照与当前服务身份不一致，禁止重试');
        }

        $snapshotKeys = self::inputKeys((string) ($snapshot['input_schema'] ?? ''));
        $currentKeys = self::inputKeys((string) ($current['input_schema'] ?? ''));
        if ($snapshotKeys !== $currentKeys) {
            throw new \RuntimeException('当前服务的请求字段已改变，不能对旧订单直接重试');
        }

        $connection = CryptoService::decrypt((string) ($current['request_url_cipher'] ?? ''));
        if (trim((string) ($connection['url'] ?? '')) === '') {
            throw new \RuntimeException('当前服务未配置有效的供应商 URL');
        }

        Db::name('order')->where('id', $order['id'])->where('status', 'query_failed')->update([
            'service_snapshot_cipher'=>CryptoService::encrypt($current),
            'updated_at'=>date('Y-m-d H:i:s'),
        ]);
        Log::info('admin retry refreshed service snapshot | order_no=' . $order['order_no'] . ' | service=' . $current['code']);
    }

    private static function inputKeys(string $schemaJson): array
    {
        $schema = json_decode($schemaJson, true);
        if (!is_array($schema)) return [];
        $keys = [];
        foreach ($schema as $field) {
            if (is_array($field) && isset($field['key'])) $keys[] = (string) $field['key'];
        }
        sort($keys, SORT_STRING);
        return $keys;
    }

    private static function orderService(array $order): ?array
    {
        if (!empty($order['service_snapshot_cipher'])) {
            try {
                $snapshot = CryptoService::decrypt((string) $order['service_snapshot_cipher']);
                if (!empty($snapshot['request_url_cipher'])) return $snapshot;
            } catch (\Throwable) {
            }
            // 有快照但快照损坏时禁止回退到后来被修改的实时配置，避免已付订单查询错服务。
            return null;
        }
        // 仅兼容升级前确实没有快照的历史订单。
        return Db::name('service')->where('id', $order['service_id'])->find() ?: null;
    }
}

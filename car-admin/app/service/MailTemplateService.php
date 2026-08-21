<?php

declare(strict_types=1);

namespace app\service;

class MailTemplateService
{
    /**
     * 每日营销数据推送。
     */
    public static function dailyDigest(array $stats): string
    {
        $date = date('Y年m月d日');
        $rows = '';
        foreach ($stats['services'] as $service) {
            $rows .= '<tr><td style="padding:8px 12px;border-bottom:1px solid #eee;">' . self::h($service['name'])
                . '</td><td style="padding:8px 12px;border-bottom:1px solid #eee;text-align:center;">' . (int) $service['count']
                . '</td><td style="padding:8px 12px;border-bottom:1px solid #eee;text-align:right;">¥' . number_format((float) $service['amount'], 2)
                . '</td></tr>';
        }

        return self::layout('每日运营数据简报', '
            <p style="color:#666;margin:0 0 20px;">以下是 ' . $date . ' 的运营数据汇总：</p>
            <table style="width:100%;border-collapse:collapse;margin-bottom:24px;">
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">总用户数</td><td style="padding:12px;text-align:right;">' . (int) $stats['total_users'] . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">今日新增用户</td><td style="padding:12px;text-align:right;">' . (int) $stats['new_users'] . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">今日订单数</td><td style="padding:12px;text-align:right;">' . (int) $stats['today_orders'] . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">今日实收</td><td style="padding:12px;text-align:right;color:#52c41a;">¥' . number_format($stats['today_net'], 2) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">今日退款</td><td style="padding:12px;text-align:right;color:#ff4d4f;">¥' . number_format($stats['today_refunds'], 2) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">今日利润</td><td style="padding:12px;text-align:right;color:#1890ff;font-weight:600;">¥' . number_format($stats['today_profit'], 2) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">本月累计实收</td><td style="padding:12px;text-align:right;">¥' . number_format($stats['month_net'], 2) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">本月累计利润</td><td style="padding:12px;text-align:right;font-weight:600;">¥' . number_format($stats['month_profit'], 2) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">查询成功率</td><td style="padding:12px;text-align:right;">' . $stats['success_rate'] . '%</td></tr>
            </table>
            ' . ($rows !== '' ? '<h3 style="color:#333;font-size:16px;margin:0 0 12px;">服务订单分布</h3>
            <table style="width:100%;border-collapse:collapse;">
                <thead><tr>
                    <th style="padding:8px 12px;background:#f8f9fa;text-align:left;border-bottom:2px solid #ddd;">服务名称</th>
                    <th style="padding:8px 12px;background:#f8f9fa;text-align:center;border-bottom:2px solid #ddd;">订单数</th>
                    <th style="padding:8px 12px;background:#f8f9fa;text-align:right;border-bottom:2px solid #ddd;">金额</th>
                </tr></thead>
                <tbody>' . $rows . '</tbody>
            </table>' : '') . '
            <p style="color:#999;font-size:12px;margin:24px 0 0;">此邮件由系统自动发送，数据截至 ' . date('H:i:s') . '。</p>
        ');
    }

    /**
     * 支付成功通知。
     */
    public static function paymentSuccess(array $order): string
    {
        return self::layout('💰 支付成功通知', '
            <p style="color:#666;margin:0 0 20px;">用户已完成支付，订单信息如下：</p>
            <table style="width:100%;border-collapse:collapse;">
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;width:120px;">订单号</td><td style="padding:12px;">' . self::h($order['order_no']) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">服务名称</td><td style="padding:12px;">' . self::h($order['service_name']) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">支付金额</td><td style="padding:12px;color:#52c41a;font-weight:600;">¥' . number_format((float) $order['amount'], 2) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">支付时间</td><td style="padding:12px;">' . self::h($order['paid_at'] ?? date('Y-m-d H:i:s')) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">用户ID</td><td style="padding:12px;">' . (int) $order['user_id'] . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">输入摘要</td><td style="padding:12px;">' . self::h($order['input_summary'] ?? '-') . '</td></tr>
            </table>
            <p style="color:#999;font-size:12px;margin:24px 0 0;">系统将自动发起供应商查询。</p>
        ');
    }

    /**
     * 查询成功通知。
     */
    public static function querySuccess(array $order): string
    {
        return self::layout('✅ 查询成功通知', '
            <p style="color:#666;margin:0 0 20px;">订单查询已成功完成：</p>
            <table style="width:100%;border-collapse:collapse;">
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;width:120px;">订单号</td><td style="padding:12px;">' . self::h($order['order_no']) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">服务名称</td><td style="padding:12px;">' . self::h($order['service_name']) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">支付金额</td><td style="padding:12px;">¥' . number_format((float) $order['amount'], 2) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">接口成本</td><td style="padding:12px;">¥' . number_format((float) ($order['cost_amount'] ?? 0), 4) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">完成时间</td><td style="padding:12px;">' . self::h($order['queried_at'] ?? date('Y-m-d H:i:s')) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">输入摘要</td><td style="padding:12px;">' . self::h($order['input_summary'] ?? '-') . '</td></tr>
            </table>
        ');
    }

    /**
     * 查询异常通知。
     */
    public static function queryFailed(array $order, string $error): string
    {
        return self::layout('⚠️ 查询异常通知', '
            <p style="color:#666;margin:0 0 20px;">订单查询出现异常，请及时处理：</p>
            <table style="width:100%;border-collapse:collapse;">
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;width:120px;">订单号</td><td style="padding:12px;">' . self::h($order['order_no']) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">服务名称</td><td style="padding:12px;">' . self::h($order['service_name']) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">支付金额</td><td style="padding:12px;">¥' . number_format((float) $order['amount'], 2) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">失败原因</td><td style="padding:12px;color:#ff4d4f;">' . self::h($error) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">供应商业务码</td><td style="padding:12px;">' . self::h($order['provider_code'] ?? '-') . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">供应商请求ID</td><td style="padding:12px;">' . self::h($order['provider_request_id'] ?? '-') . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">查询次数</td><td style="padding:12px;">' . (int) ($order['query_attempts'] ?? 0) . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">输入摘要</td><td style="padding:12px;">' . self::h($order['input_summary'] ?? '-') . '</td></tr>
            </table>
            <p style="color:#ff4d4f;font-size:13px;margin:20px 0 0;">请登录后台查看订单详情并处理。可尝试人工重试查询或发起退款。</p>
        ');
    }

    /**
     * 测试邮件。
     */
    public static function test(): string
    {
        return self::layout('SMTP 测试邮件', '
            <p style="color:#666;margin:0 0 20px;">恭喜！您的邮件服务配置正确，测试邮件已成功发送。</p>
            <table style="width:100%;border-collapse:collapse;">
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;width:120px;">发送时间</td><td style="padding:12px;">' . date('Y-m-d H:i:s') . '</td></tr>
                <tr><td style="padding:12px;background:#f8f9fa;font-weight:600;">服务器</td><td style="padding:12px;">' . self::h(ConfigService::value('smtp_host')) . '</td></tr>
            </table>
            <p style="color:#52c41a;font-size:13px;margin:20px 0 0;">邮件服务已就绪，可以开始接收系统通知了。</p>
        ');
    }

    private static function layout(string $title, string $body): string
    {
        $siteName = ConfigService::value('smtp_from_name', '车辆查询系统');
        return '<!DOCTYPE html><html><head><meta charset="UTF-8"></head><body style="margin:0;padding:20px;background:#f5f5f5;font-family:-apple-system,BlinkMacSystemFont,\'Segoe UI\',Roboto,sans-serif;">
            <div style="max-width:600px;margin:0 auto;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.06);">
                <div style="background:linear-gradient(135deg,#1890ff,#096dd9);padding:24px 32px;">
                    <h1 style="color:#fff;margin:0;font-size:20px;">' . self::h($title) . '</h1>
                    <p style="color:rgba(255,255,255,0.85);margin:8px 0 0;font-size:13px;">' . self::h($siteName) . '</p>
                </div>
                <div style="padding:32px;">' . $body . '</div>
                <div style="padding:16px 32px;background:#fafafa;border-top:1px solid #eee;text-align:center;">
                    <p style="color:#bbb;font-size:11px;margin:0;">本邮件由系统自动发送，请勿直接回复</p>
                </div>
            </div>
        </body></html>';
    }

    private static function h(string $value): string
    {
        return htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
    }
}

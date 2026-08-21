<?php

declare(strict_types=1);

namespace app\command;

use app\service\ConfigService;
use app\service\MailService;
use app\service\MailTemplateService;
use think\console\Command;
use think\console\Input;
use think\console\Output;
use think\facade\Db;

class DailyEmailCommand extends Command
{
    protected function configure(): void
    {
        $this->setName('car:daily-email')->setDescription('发送每日营销数据邮件推送');
    }

    protected function execute(Input $input, Output $output): int
    {
        if (ConfigService::value('email_notify_daily') !== '1') {
            $output->writeln('<comment>每日邮件推送未开启，跳过。</comment>');
            return 0;
        }
        if (!MailService::available()) {
            $output->writeln('<comment>邮件服务未配置，跳过。</comment>');
            return 0;
        }
        $recipients = MailService::recipients();
        if (!$recipients) {
            $output->writeln('<comment>未配置收件人，跳过。</comment>');
            return 0;
        }

        $stats = $this->collectStats();
        $html = MailTemplateService::dailyDigest($stats);
        $subject = '每日运营简报 - ' . date('Y年m月d日');

        try {
            MailService::notify($subject, $html);
            $output->writeln('<info>每日营销邮件已发送给 ' . count($recipients) . ' 位收件人。</info>');
        } catch (\Throwable $error) {
            $output->writeln('<error>邮件发送失败：' . $error->getMessage() . '</error>');
            return 1;
        }

        return 0;
    }

    private function collectStats(): array
    {
        $todayStart = date('Y-m-d 00:00:00');
        $monthStart = date('Y-m-01 00:00:00');
        $yesterdayStart = date('Y-m-d 00:00:00', strtotime('-1 day'));

        $todayOrders = (int) Db::name('order')->where('created_at', '>=', $todayStart)->count();
        $todayNet = (float) Db::name('order')->whereIn('status', ['success', 'query_failed'])->where('paid_at', '>=', $todayStart)->sum('amount');
        $todayRefunds = (float) Db::name('payment')->where('refunded_at', '>=', $todayStart)->sum('refund_amount');
        $todayManualRefunds = (float) Db::name('order')->whereNotNull('manual_refund_time')->where('paid_at', '>=', $todayStart)->sum('manual_refund_amount');
        $todayCost = (float) Db::name('order')->where('queried_at', '>=', $todayStart)->sum('cost_amount');

        $monthNet = (float) Db::name('order')->whereIn('status', ['success', 'query_failed'])->where('paid_at', '>=', $monthStart)->sum('amount');
        $monthRefunds = (float) Db::name('payment')->where('refunded_at', '>=', $monthStart)->sum('refund_amount');
        $monthManualRefunds = (float) Db::name('order')->whereNotNull('manual_refund_time')->where('paid_at', '>=', $monthStart)->sum('manual_refund_amount');
        $monthCost = (float) Db::name('order')->where('queried_at', '>=', $monthStart)->sum('cost_amount');

        $totalUsers = (int) Db::name('user')->count();
        $newUsers = (int) Db::name('user')->where('created_at', '>=', $todayStart)->count();

        // 查询成功率
        $totalQueried = (int) Db::name('order')->whereIn('status', ['success', 'query_failed'])->count();
        $successQueried = (int) Db::name('order')->where('status', 'success')->count();
        $successRate = $totalQueried > 0 ? round($successQueried / $totalQueried * 100, 1) : 0;

        // 服务订单分布
        $services = Db::name('order')
            ->fieldRaw('service_name AS name, COUNT(*) AS `count`, SUM(amount) AS amount')
            ->where('created_at', '>=', $todayStart)
            ->group('service_name')
            ->order('`count`', 'desc')
            ->limit(8)
            ->select()->toArray();

        return [
            'total_users' => $totalUsers,
            'new_users' => $newUsers,
            'today_orders' => $todayOrders,
            'today_net' => round($todayNet, 2),
            'today_refunds' => round($todayRefunds + $todayManualRefunds, 2),
            'today_profit' => round($todayNet - $todayRefunds - $todayManualRefunds - $todayCost, 2),
            'month_net' => round($monthNet, 2),
            'month_profit' => round($monthNet - $monthRefunds - $monthManualRefunds - $monthCost, 2),
            'success_rate' => $successRate,
            'services' => $services,
        ];
    }
}

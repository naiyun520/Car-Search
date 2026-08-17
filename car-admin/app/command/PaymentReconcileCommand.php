<?php

declare(strict_types=1);

namespace app\command;

use app\service\PaymentLifecycleService;
use think\console\Command;
use think\console\Input;
use think\console\Output;
use think\facade\Db;

class PaymentReconcileCommand extends Command
{
    protected function configure(): void
    {
        $this->setName('car:payment-reconcile')->setDescription('核验微信虚拟支付、发货与退款状态');
    }

    protected function execute(Input $input, Output $output): int
    {
        $lock = Db::query("SELECT GET_LOCK('car_payment_reconcile',0) acquired");
        if ((int) ($lock[0]['acquired'] ?? 0) !== 1) {
            $output->writeln('<comment>已有支付补偿任务运行，本次跳过。</comment>');
            return 0;
        }
        try {
            $result = PaymentLifecycleService::reconcilePending(20);
        } finally {
            Db::query("SELECT RELEASE_LOCK('car_payment_reconcile')");
        }
        $output->writeln(sprintf(
            '<info>支付状态核验完成：支付检查 %d，确认 %d；查询成功 %d，卡单恢复 %d；发货检查 %d，成功 %d；退款检查 %d，完成 %d。</info>',
            $result['payment_checked'],
            $result['paid'],
            $result['queried'],
            $result['recovered'],
            $result['delivery_checked'],
            $result['delivered'],
            $result['refund_checked'],
            $result['refunded']
        ));
        return 0;
    }
}

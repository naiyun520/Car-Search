<?php

declare(strict_types=1);

namespace app\command;

use think\console\Command;
use think\console\Input;
use think\console\Output;
use think\facade\Db;

class CleanupCommand extends Command
{
    protected function configure(): void
    {
        $this->setName('car:cleanup')->setDescription('清理过期令牌和敏感查询数据');
    }

    protected function execute(Input $input, Output $output): int
    {
        $now = date('Y-m-d H:i:s');
        Db::name('user_token')->where('expires_at', '<', $now)->delete();
        Db::name('admin_token')->where('expires_at', '<', $now)->delete();
        Db::name('admin_login_attempt')->where('updated_at', '<', date('Y-m-d H:i:s', strtotime('-1 day')))->delete();
        $days = max(1, (int) (Db::name('setting')->where('key', 'privacy_retention_days')->value('value') ?: 30));
        $cutoff = date('Y-m-d H:i:s', strtotime("-{$days} days"));
        Db::name('order')->where('created_at', '<', $cutoff)
            ->where('status', '<>', 'querying')
            ->update(['input_cipher'=>'','input_summary'=>'','result_cipher'=>null,'updated_at'=>$now]);
        $output->writeln('<info>清理完成。</info>');
        return 0;
    }
}

<?php

declare(strict_types=1);

namespace app\command;

use think\console\Command;
use think\console\Input;
use think\console\input\Argument;
use think\console\Output;
use think\facade\Db;

class AdminResetCommand extends Command
{
    protected function configure(): void
    {
        $this->setName('car:admin-reset')
            ->addArgument('username', Argument::OPTIONAL, '管理员账号', 'admin')
            ->setDescription('生成管理员一次性登录密码');
    }

    protected function execute(Input $input, Output $output): int
    {
        $username = (string) $input->getArgument('username');
        $admin = Db::name('admin')->where('username', $username)->find();
        if (!$admin) throw new \RuntimeException('管理员账号不存在');
        $password = 'R' . bin2hex(random_bytes(8)) . '9';
        Db::transaction(function () use ($admin, $password) {
            Db::name('admin')->where('id', $admin['id'])->update([
                'password_hash' => password_hash($password, PASSWORD_DEFAULT),
                'must_change_password' => 1,
                'status' => 1,
                'updated_at' => date('Y-m-d H:i:s'),
            ]);
            Db::name('admin_token')->where('admin_id', $admin['id'])->delete();
        });
        $output->writeln('<info>一次性密码：' . $password . '</info>');
        $output->writeln('<comment>登录后请立即在“系统与支付”中修改密码。</comment>');
        return 0;
    }
}

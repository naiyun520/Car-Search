<?php

declare(strict_types=1);

namespace app\command;

use think\console\Command;
use think\console\Input;
use think\console\Output;
use think\facade\Db;

class UpgradeCommand extends Command
{
    protected function configure(): void
    {
        $this->setName('car:upgrade')->setDescription('升级车辆查询系统数据库结构');
    }

    protected function execute(Input $input, Output $output): int
    {
        if (trim((string) env('database.database', '')) === '' || trim((string) env('database.username', '')) === '') {
            throw new \RuntimeException('未读取到数据库配置，请恢复项目根目录正式 .env 后再执行升级');
        }
        $prefix = (string) env('database.prefix', 'ci_');
        if (!preg_match('/^[a-zA-Z0-9_]{1,20}$/', $prefix)) throw new \RuntimeException('DB_PREFIX 格式不正确');
        $sql = file_get_contents(root_path() . 'database/upgrade_20260807.sql');
        if ($sql === false) throw new \RuntimeException('升级文件不存在');
        $sql = str_replace('`ci_', '`' . $prefix, $sql);
        foreach (array_filter(array_map('trim', explode(';', $sql))) as $statement) Db::execute($statement);
        $serviceTable = $prefix . 'service';
        $feedbackTable = $prefix . 'feedback';
        $userTable = $prefix . 'user';
        $userColumns = array_column(Db::query("SHOW COLUMNS FROM `{$userTable}`"), 'Field');
        $this->addColumn($userTable, $userColumns, 'session_key_cipher', 'TEXT NULL AFTER `phone`');
        Db::execute("ALTER TABLE `{$userTable}` AUTO_INCREMENT = 520");
        $serviceColumns = array_column(Db::query("SHOW COLUMNS FROM `{$serviceTable}`"), 'Field');
        $isFirstDynamicUpgrade = !in_array('request_url_cipher', $serviceColumns, true);
        $isFirstPaymentProductUpgrade = !in_array('payment_product_id', $serviceColumns, true);
        $this->addColumn($serviceTable, $serviceColumns, 'payment_product_id', "VARCHAR(128) NOT NULL DEFAULT '' AFTER `code`");
        $this->addColumn($serviceTable, $serviceColumns, 'request_url_cipher', 'TEXT NULL AFTER `provider_key_cipher`');
        $this->addColumn($serviceTable, $serviceColumns, 'request_method', "VARCHAR(10) NOT NULL DEFAULT 'GET' AFTER `request_url_cipher`");
        $this->addColumn($serviceTable, $serviceColumns, 'response_code_path', "VARCHAR(100) NOT NULL DEFAULT 'code' AFTER `request_method`");
        $this->addColumn($serviceTable, $serviceColumns, 'response_success_value', "VARCHAR(50) NOT NULL DEFAULT '200' AFTER `response_code_path`");
        $this->addColumn($serviceTable, $serviceColumns, 'response_data_path', "VARCHAR(100) NOT NULL DEFAULT 'data' AFTER `response_success_value`");
        Db::execute("ALTER TABLE `{$serviceTable}` MODIFY `icon` VARCHAR(255) NOT NULL DEFAULT ''");
        $settingTable = $prefix . 'setting';
        foreach (['operator_name'=>'','privacy_contact'=>'','privacy_effective_date'=>'2026-08-15'] as $key => $value) {
            Db::execute("INSERT IGNORE INTO `{$settingTable}` (`key`,`value`,`updated_at`) VALUES (?,?,?)", [$key,$value,date('Y-m-d H:i:s')]);
        }
        Db::execute("ALTER TABLE `{$serviceTable}` MODIFY `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0维护 1运行 2隐藏'");
        if ($isFirstDynamicUpgrade) Db::execute("UPDATE `{$serviceTable}` SET `status` = 0, `updated_at` = ?", [date('Y-m-d H:i:s')]);
        if ($isFirstPaymentProductUpgrade) Db::execute("UPDATE `{$serviceTable}` SET `status` = 0, `updated_at` = ?", [date('Y-m-d H:i:s')]);
        $feedbackColumns = array_column(Db::query("SHOW COLUMNS FROM `{$feedbackTable}`"), 'Field');
        $this->addColumn($feedbackTable, $feedbackColumns, 'replied_at', 'DATETIME NULL AFTER `reply`');
        $orderTable = $prefix . 'order';
        $orderColumns = array_column(Db::query("SHOW COLUMNS FROM `{$orderTable}`"), 'Field');
        $isFirstCostUpgrade = !in_array('cost_amount', $orderColumns, true);
        $this->addColumn($orderTable, $orderColumns, 'cost_amount', 'DECIMAL(10,4) NOT NULL DEFAULT 0 AFTER `amount`');
        $this->addColumn($orderTable, $orderColumns, 'service_snapshot_cipher', 'TEXT NULL AFTER `input_cipher`');
        $this->addColumn($orderTable, $orderColumns, 'request_key', 'VARCHAR(64) NULL AFTER `order_no`');
        $this->addColumn($orderTable, $orderColumns, 'query_attempts', 'INT UNSIGNED NOT NULL DEFAULT 0 AFTER `provider_request_id`');
        $this->addColumn($orderTable, $orderColumns, 'query_last_error', 'VARCHAR(500) NULL AFTER `query_attempts`');
        $this->addIndex($orderTable,'uk_user_request','UNIQUE INDEX','(`user_id`,`request_key`)');
        $this->addIndex($orderTable,'idx_status_updated','INDEX','(`status`,`updated_at`)');
        if ($isFirstCostUpgrade) Db::execute("UPDATE `{$orderTable}` o LEFT JOIN `{$serviceTable}` s ON s.id = o.service_id SET o.cost_amount = COALESCE(s.cost_price, 0)");
        $paymentTable = $prefix . 'payment';
        $paymentColumns = array_column(Db::query("SHOW COLUMNS FROM `{$paymentTable}`"), 'Field');
        $this->addColumn($paymentTable, $paymentColumns, 'pay_env', 'TINYINT NOT NULL DEFAULT 0 AFTER `amount`');
        $this->addColumn($paymentTable, $paymentColumns, 'last_checked_at', 'DATETIME NULL AFTER `callback_payload`');
        $this->addColumn($paymentTable, $paymentColumns, 'delivery_status', "VARCHAR(20) NOT NULL DEFAULT 'pending' AFTER `callback_payload`");
        $this->addColumn($paymentTable, $paymentColumns, 'delivery_attempts', 'INT UNSIGNED NOT NULL DEFAULT 0 AFTER `delivery_status`');
        $this->addColumn($paymentTable, $paymentColumns, 'delivery_attempted_at', 'DATETIME NULL AFTER `delivery_attempts`');
        $this->addColumn($paymentTable, $paymentColumns, 'delivery_last_error', 'VARCHAR(500) NULL AFTER `delivery_attempted_at`');
        $this->addColumn($paymentTable, $paymentColumns, 'delivered_at', 'DATETIME NULL AFTER `delivery_last_error`');
        $this->addColumn($paymentTable, $paymentColumns, 'refund_order_no', 'VARCHAR(32) NULL AFTER `delivered_at`');
        $this->addColumn($paymentTable, $paymentColumns, 'refund_status', "VARCHAR(20) NOT NULL DEFAULT 'none' AFTER `refund_order_no`");
        $this->addColumn($paymentTable, $paymentColumns, 'refund_amount', 'DECIMAL(10,2) NULL AFTER `refund_status`');
        $this->addColumn($paymentTable, $paymentColumns, 'refund_reason', 'VARCHAR(10) NULL AFTER `refund_amount`');
        $this->addColumn($paymentTable, $paymentColumns, 'refund_from_status', 'VARCHAR(24) NULL AFTER `refund_reason`');
        $this->addColumn($paymentTable, $paymentColumns, 'refund_payload', 'TEXT NULL AFTER `refund_from_status`');
        $this->addColumn($paymentTable, $paymentColumns, 'refund_last_error', 'VARCHAR(500) NULL AFTER `refund_payload`');
        $this->addColumn($paymentTable, $paymentColumns, 'refund_requested_at', 'DATETIME NULL AFTER `refund_last_error`');
        $this->addColumn($paymentTable, $paymentColumns, 'refunded_at', 'DATETIME NULL AFTER `refund_requested_at`');
        $this->addIndex($paymentTable,'uk_refund_order_no','UNIQUE INDEX','(`refund_order_no`)');
        $this->addIndex($paymentTable,'idx_payment_check','INDEX','(`status`,`last_checked_at`)');
        $this->addIndex($paymentTable,'idx_delivery_status','INDEX','(`delivery_status`,`delivery_attempted_at`)');
        $this->addIndex($paymentTable,'idx_refund_status','INDEX','(`refund_status`,`updated_at`)');
        Db::execute("UPDATE `{$paymentTable}` p JOIN `{$orderTable}` o ON o.id=p.order_id SET p.refund_status='refunded', p.refunded_at=COALESCE(p.refunded_at,o.updated_at) WHERE o.status='refunded' AND p.refund_status<>'refunded'");
        $runtime = root_path() . 'runtime';
        if (!is_dir($runtime) && !mkdir($runtime, 0750, true) && !is_dir($runtime)) throw new \RuntimeException('无法创建 runtime 目录');
        file_put_contents($runtime . DIRECTORY_SEPARATOR . 'install.lock', json_encode(['upgraded_at'=>date(DATE_ATOM),'version'=>'3.0.0'], JSON_UNESCAPED_UNICODE), LOCK_EX);
        $output->writeln('<info>数据库升级完成。</info>');
        return 0;
    }

    private function addColumn(string $table, array &$columns, string $column, string $definition): void
    {
        if (in_array($column, $columns, true)) return;
        Db::execute("ALTER TABLE `{$table}` ADD COLUMN `{$column}` {$definition}");
        $columns[] = $column;
    }

    private function addIndex(string $table, string $index, string $type, string $columns): void
    {
        $indexes = array_column(Db::query("SHOW INDEX FROM `{$table}`"), 'Key_name');
        if (in_array($index,$indexes,true)) return;
        Db::execute("ALTER TABLE `{$table}` ADD {$type} `{$index}` {$columns}");
    }
}

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `openid` varchar(64) NOT NULL,
  `unionid` varchar(64) DEFAULT NULL,
  `nickname` varchar(64) NOT NULL DEFAULT '微信用户',
  `avatar_url` varchar(500) NOT NULL DEFAULT '',
  `phone` varchar(32) NOT NULL DEFAULT '',
  `session_key_cipher` text,
  `status` tinyint NOT NULL DEFAULT 1,
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_openid` (`openid`), KEY `idx_status_created` (`status`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=520 DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_user_token` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `token_hash` char(64) NOT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_token_hash` (`token_hash`), KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_service` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `code` varchar(40) NOT NULL,
  `payment_product_id` varchar(128) NOT NULL DEFAULT '',
  `provider_api_id` int unsigned NOT NULL,
  `name` varchar(80) NOT NULL,
  `short_name` varchar(40) NOT NULL,
  `description` varchar(255) NOT NULL DEFAULT '',
  `icon` varchar(255) NOT NULL DEFAULT '',
  `input_schema` json NOT NULL,
  `result_schema` json NOT NULL,
  `provider_key_cipher` text NOT NULL,
  `request_url_cipher` text NOT NULL,
  `request_method` varchar(10) NOT NULL DEFAULT 'GET',
  `response_code_path` varchar(100) NOT NULL DEFAULT 'code',
  `response_success_value` varchar(50) NOT NULL DEFAULT '200',
  `response_data_path` varchar(100) NOT NULL DEFAULT 'data',
  `sale_price` decimal(10,2) NOT NULL,
  `cost_price` decimal(10,4) NOT NULL DEFAULT 0,
  `sort` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0维护 1运行 2隐藏',
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_code` (`code`), KEY `idx_status_sort` (`status`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_order` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_no` varchar(32) NOT NULL,
  `request_key` varchar(64) DEFAULT NULL,
  `user_id` bigint unsigned NOT NULL,
  `service_id` int unsigned NOT NULL,
  `service_name` varchar(80) NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `cost_amount` decimal(10,4) NOT NULL DEFAULT 0,
  `status` varchar(24) NOT NULL DEFAULT 'pending_payment',
  `input_cipher` text NOT NULL,
  `service_snapshot_cipher` text,
  `input_summary` varchar(100) NOT NULL DEFAULT '',
  `result_cipher` mediumtext,
  `provider_code` varchar(20) DEFAULT NULL,
  `provider_request_id` varchar(100) DEFAULT NULL,
  `query_attempts` int unsigned NOT NULL DEFAULT 0,
  `query_last_error` varchar(500) DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `queried_at` datetime DEFAULT NULL,
  `expired_at` datetime NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `manual_refund_amount` decimal(10,2) NOT NULL DEFAULT 0.00,
  `manual_refund_time` datetime DEFAULT NULL,
  `manual_refund_remark` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_order_no` (`order_no`), UNIQUE KEY `uk_user_request` (`user_id`,`request_key`), KEY `idx_user_created` (`user_id`,`created_at`), KEY `idx_status_updated` (`status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_payment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_id` bigint unsigned NOT NULL,
  `order_no` varchar(32) NOT NULL,
  `transaction_id` varchar(100) DEFAULT NULL,
  `wechat_order_id` varchar(100) DEFAULT NULL,
  `wechat_pay_transaction_id` varchar(100) DEFAULT NULL,
  `channel_order_id` varchar(100) DEFAULT NULL,
  `wechat_order_type` tinyint DEFAULT NULL,
  `amount` decimal(10,2) NOT NULL,
  `pay_env` tinyint NOT NULL DEFAULT 0,
  `status` varchar(20) NOT NULL DEFAULT 'created',
  `callback_payload` text,
  `last_checked_at` datetime DEFAULT NULL,
  `delivery_status` varchar(20) NOT NULL DEFAULT 'pending',
  `delivery_attempts` int unsigned NOT NULL DEFAULT 0,
  `delivery_attempted_at` datetime DEFAULT NULL,
  `delivery_last_error` varchar(500) DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `refund_order_no` varchar(100) DEFAULT NULL,
  `refund_status` varchar(20) NOT NULL DEFAULT 'none',
  `refund_source` varchar(20) DEFAULT NULL,
  `refund_amount` decimal(10,2) DEFAULT NULL,
  `refund_reason` varchar(10) DEFAULT NULL,
  `refund_from_status` varchar(24) DEFAULT NULL,
  `refund_payload` text,
  `refund_last_error` varchar(500) DEFAULT NULL,
  `refund_requested_at` datetime DEFAULT NULL,
  `refunded_at` datetime DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_order_id` (`order_id`), UNIQUE KEY `uk_transaction` (`transaction_id`), UNIQUE KEY `uk_refund_order_no` (`refund_order_no`), KEY `idx_wechat_order_id` (`wechat_order_id`), KEY `idx_wechat_pay_transaction_id` (`wechat_pay_transaction_id`), KEY `idx_channel_order_id` (`channel_order_id`), KEY `idx_payment_check` (`status`,`last_checked_at`), KEY `idx_delivery_status` (`delivery_status`,`delivery_attempted_at`), KEY `idx_refund_status` (`refund_status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_wechat_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `event_key` char(64) NOT NULL,
  `event_type` varchar(64) NOT NULL,
  `order_no` varchar(32) DEFAULT NULL,
  `request_cipher` text NOT NULL,
  `response_cipher` text,
  `process_status` varchar(20) NOT NULL DEFAULT 'received',
  `last_error` varchar(500) DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_event_key` (`event_key`), KEY `idx_event_order` (`order_no`,`created_at`), KEY `idx_event_status` (`process_status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_announcement` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `title` varchar(100) NOT NULL,
  `content` text NOT NULL,
  `suppress_hours` int unsigned NOT NULL DEFAULT 24,
  `status` tinyint NOT NULL DEFAULT 1,
  `start_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_active` (`status`,`start_at`,`end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_feedback` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `type` varchar(30) NOT NULL,
  `content` varchar(1000) NOT NULL,
  `contact` varchar(100) NOT NULL DEFAULT '',
  `status` varchar(20) NOT NULL DEFAULT 'pending',
  `reply` varchar(1000) NOT NULL DEFAULT '',
  `replied_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_status_created` (`status`,`created_at`), KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_setting` (
  `key` varchar(80) NOT NULL,
  `value` text NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_secure_setting` (
  `key` varchar(80) NOT NULL,
  `value_cipher` text NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_admin` (
  `id` int unsigned NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `must_change_password` tinyint NOT NULL DEFAULT 1,
  `status` tinyint NOT NULL DEFAULT 1,
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_admin_token` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` int unsigned NOT NULL,
  `token_hash` char(64) NOT NULL,
  `expires_at` datetime NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_token` (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_admin_login_attempt` (
  `identity_hash` char(64) NOT NULL,
  `fail_count` int unsigned NOT NULL DEFAULT 0,
  `locked_until` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`identity_hash`), KEY `idx_locked` (`locked_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_audit_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `admin_id` int unsigned NOT NULL,
  `action` varchar(80) NOT NULL,
  `target_type` varchar(40) NOT NULL,
  `target_id` varchar(64) NOT NULL DEFAULT '',
  `detail` json DEFAULT NULL,
  `ip` varchar(45) NOT NULL DEFAULT '',
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`), KEY `idx_admin_created` (`admin_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `ci_setting` (`key`,`value`,`updated_at`) VALUES
('customer_service_phone','400-000-0000',NOW()),
('customer_service_hours','工作日 09:00-18:00',NOW()),
('operator_name','',NOW()),
('privacy_contact','',NOW()),
('privacy_effective_date','2026-08-15',NOW()),
('disclaimer','查询结果来自依法授权的数据服务，仅供本人合法用途参考。用户须确保已取得相关主体授权，禁止用于骚扰、歧视、非法调查或其他违法用途。因数据源更新存在延迟，结果不作为行政、司法或交易决策的唯一依据。',NOW()),
('privacy_retention_days','30',NOW()),
('payment_enabled','0',NOW()),
('wechat_app_id','',NOW()),
('wechat_offer_id','',NOW()),
('wechat_pay_env','0',NOW())
ON DUPLICATE KEY UPDATE `key`=VALUES(`key`);


CREATE TABLE IF NOT EXISTS `ci_secure_setting` (
  `key` varchar(80) NOT NULL,
  `value_cipher` text NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `ci_admin_login_attempt` (
  `identity_hash` char(64) NOT NULL,
  `fail_count` int unsigned NOT NULL DEFAULT 0,
  `locked_until` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`identity_hash`), KEY `idx_locked` (`locked_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `ci_setting` (`key`,`value`,`updated_at`) VALUES
('wechat_app_id','',NOW()),
('wechat_offer_id','',NOW()),
('wechat_pay_env','0',NOW())
ON DUPLICATE KEY UPDATE `key`=VALUES(`key`);

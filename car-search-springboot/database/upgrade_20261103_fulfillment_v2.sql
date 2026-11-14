-- 履约任务、余额、回调收件箱、结果修订与统一保留期升级。
-- 2026-11-04：供应商报告仅主动获取，不再新增回调收件箱和验签字段。
-- 执行前必须备份；真实供应商开关默认关闭。表、列和索引均按存在性检查，可重复执行。

CREATE TABLE IF NOT EXISTS ci_query_job (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  service_id INT UNSIGNED NOT NULL,
  provider_id INT UNSIGNED NULL,
  job_type VARCHAR(20) NOT NULL,
  state VARCHAR(32) NOT NULL,
  provider_request_id VARCHAR(100) NULL,
  submit_attempts INT UNSIGNED NOT NULL DEFAULT 0,
  fetch_attempts INT UNSIGNED NOT NULL DEFAULT 0,
  max_fetch_attempts INT UNSIGNED NOT NULL DEFAULT 20,
  interval_seconds INT UNSIGNED NOT NULL DEFAULT 60,
  next_run_at DATETIME NULL,
  deadline_at DATETIME NULL,
  lock_owner VARCHAR(64) NULL,
  locked_until DATETIME NULL,
  version INT UNSIGNED NOT NULL DEFAULT 0,
  last_error_class VARCHAR(32) NULL,
  last_error_code VARCHAR(80) NULL,
  last_error_message VARCHAR(500) NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  completed_at DATETIME NULL,
  UNIQUE KEY uk_query_job_order(order_id),
  UNIQUE KEY uk_query_job_provider_request(provider_id, provider_request_id),
  KEY idx_query_job_due(state, next_run_at, locked_until),
  KEY idx_query_job_service(service_id, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ci_provider_call (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NULL,
  job_id BIGINT UNSIGNED NULL,
  provider_id INT UNSIGNED NULL,
  service_id INT UNSIGNED NULL,
  stage VARCHAR(24) NOT NULL,
  call_status VARCHAR(20) NOT NULL,
  billing_status VARCHAR(20) NOT NULL DEFAULT 'not_billable',
  cost_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  provider_request_id VARCHAR(100) NULL,
  http_status INT NULL,
  business_code VARCHAR(80) NULL,
  duration_ms BIGINT UNSIGNED NULL,
  request_hash VARCHAR(64) NULL,
  response_hash VARCHAR(64) NULL,
  response_size INT UNSIGNED NULL,
  response_keys VARCHAR(500) NULL,
  error_class VARCHAR(32) NULL,
  error_message VARCHAR(500) NULL,
  started_at DATETIME NOT NULL,
  finished_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  KEY idx_provider_call_order(order_id, created_at),
  KEY idx_provider_call_job(job_id, created_at),
  KEY idx_provider_call_provider(provider_id, created_at),
  KEY idx_provider_call_billing(billing_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ci_provider_balance_snapshot (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  provider_id INT UNSIGNED NOT NULL,
  balance DECIMAL(18,4) NULL,
  source VARCHAR(24) NOT NULL,
  status VARCHAR(20) NOT NULL,
  error_code VARCHAR(80) NULL,
  error_message VARCHAR(500) NULL,
  observed_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  KEY idx_provider_balance(provider_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ci_order_result_revision (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNSIGNED NOT NULL,
  base_revision_id BIGINT UNSIGNED NULL,
  source VARCHAR(24) NOT NULL,
  result_cipher MEDIUMTEXT NOT NULL,
  change_reason VARCHAR(500) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'draft',
  created_by BIGINT UNSIGNED NULL,
  created_at DATETIME NOT NULL,
  published_at DATETIME NULL,
  KEY idx_result_revision_order(order_id, created_at),
  KEY idx_result_revision_status(status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
DROP PROCEDURE IF EXISTS add_column_if_missing$$
CREATE PROCEDURE add_column_if_missing(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name=p_table AND column_name=p_column
  ) THEN
    SET @ddl=CONCAT('ALTER TABLE `',p_table,'` ADD COLUMN `',p_column,'` ',p_definition);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DROP PROCEDURE IF EXISTS add_index_if_missing$$
CREATE PROCEDURE add_index_if_missing(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name=p_table AND index_name=p_index
  ) THEN
    SET @ddl=CONCAT('ALTER TABLE `',p_table,'` ADD INDEX `',p_index,'`(',p_columns,')');
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL add_column_if_missing('ci_service','query_job_enabled','TINYINT NOT NULL DEFAULT 0');
CALL add_column_if_missing('ci_service','poll_max_attempts','INT UNSIGNED NOT NULL DEFAULT 20');

CALL add_column_if_missing('ci_provider','balance_enabled','TINYINT NOT NULL DEFAULT 0');
CALL add_column_if_missing('ci_provider','balance_mode','VARCHAR(20) NOT NULL DEFAULT ''unsupported''');
CALL add_column_if_missing('ci_provider','balance_api_path','VARCHAR(500) NOT NULL DEFAULT ''''');
CALL add_column_if_missing('ci_provider','balance_api_version','VARCHAR(20) NOT NULL DEFAULT ''''');
CALL add_column_if_missing('ci_provider','balance_value_path','VARCHAR(100) NOT NULL DEFAULT ''''');
CALL add_column_if_missing('ci_provider','balance_refresh_minutes','INT UNSIGNED NOT NULL DEFAULT 10');

CALL add_column_if_missing('ci_order','user_visible_until','DATETIME NULL');
CALL add_column_if_missing('ci_order','admin_retention_until','DATETIME NULL');
CALL add_column_if_missing('ci_order','active_result_revision_id','BIGINT UNSIGNED NULL');
CALL add_column_if_missing('ci_order','fulfillment_version','INT UNSIGNED NOT NULL DEFAULT 0');
CALL add_index_if_missing('ci_order','idx_order_admin_retention','`admin_retention_until`,`status`');

DROP PROCEDURE add_index_if_missing;
DROP PROCEDURE add_column_if_missing;

INSERT INTO ci_setting(`key`, value, updated_at)
VALUES ('privacy_retention_days', '180', NOW())
ON DUPLICATE KEY UPDATE value='180', updated_at=VALUES(updated_at);

-- 不在本脚本中修改现有轮询参数或开启服务。服务切换到新任务前再原子写入：
-- poll_interval_sec=60, poll_timeout_sec=1200, poll_max_attempts=20, query_job_enabled=1。

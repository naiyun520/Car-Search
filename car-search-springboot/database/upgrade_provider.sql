-- 供应商管理表：存储第三方数据供应商的认证与连接配置
CREATE TABLE IF NOT EXISTS ci_provider (
  id            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(80)  NOT NULL DEFAULT '' COMMENT '供应商名称',
  code          VARCHAR(40)  NOT NULL COMMENT '唯一编码，如 qxb',
  auth_type     VARCHAR(20)  NOT NULL DEFAULT 'none' COMMENT '认证类型：none / sign_md5',
  config_cipher TEXT         NOT NULL COMMENT 'AES加密JSON：{appid, secret, version}',
  base_url      VARCHAR(500) NOT NULL DEFAULT '' COMMENT '基础URL（明文）',
  allow_http    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否允许HTTP协议',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  sort          INT          NOT NULL DEFAULT 0,
  created_at    DATETIME     NOT NULL,
  updated_at    DATETIME     NOT NULL,
  UNIQUE KEY uk_provider_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ci_service 新增接口路径列，用于供应商模式下拼接完整URL
-- 注意：添加到表末尾，避免影响现有数据导入
ALTER TABLE ci_service ADD COLUMN api_path VARCHAR(500) NOT NULL DEFAULT '';

-- ci_service 新增异步查询相关字段（两步式接口：下单→获取报告）
ALTER TABLE ci_service ADD COLUMN query_mode VARCHAR(20) NOT NULL DEFAULT 'single' COMMENT '查询模式：single单步/async异步两步';
ALTER TABLE ci_service ADD COLUMN fetch_api_path VARCHAR(500) NOT NULL DEFAULT '' COMMENT '异步模式下，获取报告的接口路径';
ALTER TABLE ci_service ADD COLUMN poll_interval_sec INT NOT NULL DEFAULT 5 COMMENT '异步模式下，轮询间隔（秒）';
ALTER TABLE ci_service ADD COLUMN poll_timeout_sec INT NOT NULL DEFAULT 120 COMMENT '异步模式下，轮询超时（秒）';
ALTER TABLE ci_service ADD COLUMN api_version VARCHAR(20) NOT NULL DEFAULT '' COMMENT '接口版本号，留空则使用供应商默认版本';
-- ========================================
-- 回调模式支持：跟踪查询状态 + 接收回调结果
-- ========================================

-- ci_service 新增回调相关字段
ALTER TABLE ci_service ADD COLUMN callback_mode VARCHAR(20) NOT NULL DEFAULT 'none'
  COMMENT '回调模式：none=不使用/callback=回调模式（超时降级轮询）';
ALTER TABLE ci_service ADD COLUMN callback_secret VARCHAR(128) NOT NULL DEFAULT ''
  COMMENT '回调签名令牌，用于验证供应商回调的 HTTP-X-CA-PROXY-SIGNATURE';
ALTER TABLE ci_service ADD COLUMN callback_timeout_sec INT NOT NULL DEFAULT 120
  COMMENT '回调超时秒数，超时后自动降级轮询供应商';

-- 查询跟踪表：记录每次查询的状态和回调结果
CREATE TABLE IF NOT EXISTS ci_query (
  id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  query_id        VARCHAR(80)  NOT NULL DEFAULT '' COMMENT '供应商返回的 query_id/code_id',
  service_id      INT UNSIGNED NOT NULL COMMENT '关联 ci_service.id',
  order_id        BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '关联 ci_order.id（可选）',
  user_id         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '发起查询的用户',
  status          VARCHAR(20)  NOT NULL DEFAULT 'pending'
    COMMENT 'pending=等待中 / processing=处理中 / completed=已完成 / failed=失败 / timeout=超时',
  request_body    TEXT         NOT NULL COMMENT '下单请求参数JSON（用于降级重试）',
  result_json     MEDIUMTEXT   NULL COMMENT '回调返回的完整结果JSON',
  result_code     VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '回调结果状态码',
  result_message  VARCHAR(500) NOT NULL DEFAULT '' COMMENT '回调结果消息',
  callback_received_at DATETIME NULL COMMENT '收到回调的时间',
  fallback_at     DATETIME     NULL COMMENT '触发降级轮询的时间',
  completed_at    DATETIME     NULL COMMENT '查询完成时间',
  created_at      DATETIME     NOT NULL,
  updated_at      DATETIME     NOT NULL,
  INDEX idx_query_id (query_id),
  INDEX idx_service_status (service_id, status),
  INDEX idx_order_id (order_id),
  INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
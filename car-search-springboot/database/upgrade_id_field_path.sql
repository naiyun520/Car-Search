-- 升级：ci_service 新增 id_field_path 字段
-- 用于配置下单接口返回的 ID 字段路径（支持点号路径，如 result.query_id）

ALTER TABLE ci_service
ADD COLUMN id_field_path VARCHAR(100) DEFAULT '' COMMENT '下单返回ID的JSON路径，如 result.query_id 或 task_id'
AFTER callback_timeout_sec;
-- 降级轮询报告接口参数名可配置（默认 query_id，部分供应商使用 order_id）
ALTER TABLE ci_service ADD COLUMN fetch_api_param_name VARCHAR(50) DEFAULT NULL COMMENT '降级轮询报告接口的查询ID参数名，默认 query_id' AFTER id_field_path;

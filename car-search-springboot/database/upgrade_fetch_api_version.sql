-- 升级：ci_service 新增 fetch_api_version 字段
-- 用于获取报告接口独立版本号（异步两步查询和回调降级轮询使用）

ALTER TABLE ci_service
ADD COLUMN fetch_api_version VARCHAR(20) DEFAULT '' COMMENT '获取报告接口版本号，为空则使用 api_version'
AFTER api_version;
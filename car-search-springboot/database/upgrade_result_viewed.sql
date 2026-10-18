-- 升级：ci_order 新增 result_viewed_at 字段
-- 用于标记单次查询结果的首次查看时间，实现"不可二次查看"

ALTER TABLE ci_order
ADD COLUMN result_viewed_at DATETIME DEFAULT NULL COMMENT '结果首次查看时间（仅单次查询）'
AFTER queried_at;
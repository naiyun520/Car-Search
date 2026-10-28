-- =====================================================================
-- 结果保留期改造：一级查询结果由「阅后销毁」改为「生成后 24 小时过期」
-- 说明：
--   1. 无需新增字段，有效期由 ci_order.queried_at + 24h 现算，
--      二级（query_mode=async 或 callback_mode=callback）不受影响，长期可查看。
--   2. 本脚本只做一次性数据清理：把历史上已被「阅后销毁」的一级订单密文清空，
--      维持用户既有认知（这些报告此前已对用户不可见）。
--   3. 建议先执行 COUNT 语句确认影响行数，再执行 UPDATE。
-- =====================================================================

-- 步骤 0：备份（可回滚，确认无误后可 DROP）
CREATE TABLE IF NOT EXISTS ci_order_result_cipher_backup_20261028 AS
SELECT o.id, o.order_no, o.result_cipher, o.result_viewed_at
FROM ci_order o
JOIN ci_service s ON s.id = o.service_id
WHERE o.result_viewed_at IS NOT NULL
  AND o.result_cipher IS NOT NULL
  AND s.query_mode = 'single'
  AND s.callback_mode = 'none'
  AND (o.provider_request_id IS NULL OR o.provider_request_id IN ('', 'null'));

-- 步骤 1：预览影响行数
SELECT COUNT(*) AS affected FROM ci_order_result_cipher_backup_20261028;

-- 步骤 2：清除历史已阅的一级订单密文（订单、金额、状态全部保留，不影响对账）
UPDATE ci_order o
JOIN ci_service s ON s.id = o.service_id
SET o.result_cipher = NULL,
    o.updated_at = NOW()
WHERE o.result_viewed_at IS NOT NULL
  AND o.result_cipher IS NOT NULL
  AND s.query_mode = 'single'
  AND s.callback_mode = 'none'
  AND (o.provider_request_id IS NULL OR o.provider_request_id IN ('', 'null'));

-- 回滚方式：
-- UPDATE ci_order o
-- JOIN ci_order_result_cipher_backup_20261028 b ON b.id = o.id
-- SET o.result_cipher = b.result_cipher
-- WHERE o.result_cipher IS NULL;

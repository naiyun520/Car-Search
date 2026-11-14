-- 先备份并停止接单，核对处理所有查询中订单，再执行本脚本。
-- 不修改供应商、接口地址、返回编号路径、报告参数名或版本。
-- 原回调业务的120秒为旧默认值，切换为0（仅受次数上限限制）；其他等待期限保留。
DROP PROCEDURE IF EXISTS migrate_active_report_polling;
DELIMITER $$
CREATE PROCEDURE migrate_active_report_polling()
BEGIN
  IF EXISTS(SELECT 1 FROM ci_query_job WHERE job_type='callback'
     AND state NOT IN('succeeded','failed','no_data','timed_out','submit_unknown','direct_unknown','stopped')) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Resolve active supplier callback jobs before upgrading';
  END IF;
  UPDATE ci_service SET query_mode='async',
    poll_interval_sec=GREATEST(60,poll_interval_sec),
    poll_timeout_sec=CASE WHEN poll_timeout_sec=120 THEN 0 ELSE poll_timeout_sec END,
    callback_mode='none',callback_secret='',callback_timeout_sec=0
    WHERE callback_mode='callback';
END$$
DELIMITER ;
CALL migrate_active_report_polling();
DROP PROCEDURE migrate_active_report_polling;
-- 历史回调列和记录留档，不再参与执行，不自动删除历史订单或报告。

-- 安卓“未查询到信息”自动退款升级说明。
-- 应用启动时 DatabaseFeatureUpgrade 会幂等添加同一字段；本文件用于手工审计和正式部署变更记录。
alter table ci_payment
    add column refund_attempts int unsigned not null default 0 after refund_status;

-- 开关默认关闭。首次在后台开启时，应用会同时写入 android_auto_refund_enabled_at，
-- 只处理开启时间之后出现的安卓未查询到信息订单，避免误退历史订单。
insert into ci_setting(`key`, value, updated_at)
values ('android_auto_refund_query_failed', '0', now())
on duplicate key update `key`=values(`key`);

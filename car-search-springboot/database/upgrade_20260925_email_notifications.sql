create table if not exists ci_email_delivery(
  id bigint unsigned not null auto_increment,
  event_key varchar(128) not null,
  event_type varchar(40) not null,
  order_id bigint unsigned null,
  recipient varchar(254) not null,
  subject varchar(255) not null,
  body mediumtext not null,
  status varchar(16) not null default 'pending',
  attempts int unsigned not null default 0,
  next_retry_at datetime not null,
  last_error varchar(500) null,
  sent_at datetime null,
  created_at datetime not null,
  updated_at datetime not null,
  primary key(id),
  unique key uk_email_event_recipient(event_key,recipient),
  key idx_email_delivery_pending(status,next_retry_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

insert ignore into ci_setting(`key`,value,updated_at)
select 'email_notify_enabled',if(exists(select 1 from ci_setting where `key` in ('email_notify_payment','email_notify_query_success','email_notify_query_failed') and value='1'),'1','0'),now();
insert ignore into ci_setting(`key`,value,updated_at) select 'email_event_payment_success',coalesce((select value from ci_setting where `key`='email_notify_payment' limit 1),'0'),now();
insert ignore into ci_setting(`key`,value,updated_at) select 'email_event_query_success',coalesce((select value from ci_setting where `key`='email_notify_query_success' limit 1),'0'),now();
insert ignore into ci_setting(`key`,value,updated_at) select 'email_event_query_failed',coalesce((select value from ci_setting where `key`='email_notify_query_failed' limit 1),'0'),now();
insert ignore into ci_setting(`key`,value,updated_at) values
('email_event_refund_result','0',now()),
('email_event_provider_balance','0',now());

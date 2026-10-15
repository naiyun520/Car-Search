-- Spring Boot also applies this additive upgrade idempotently at startup.
-- This file documents the production schema change for manual DBA review.
create table if not exists ci_service_category(
  id int unsigned not null auto_increment,
  code varchar(40) not null,
  name varchar(40) not null,
  icon varchar(40) not null default 'grid',
  sort int not null default 0,
  status tinyint not null default 1,
  created_at datetime not null,
  updated_at datetime not null,
  primary key(id),
  unique key uk_service_category_code(code),
  key idx_service_category_status_sort(status,sort)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- Existing installations are upgraded by DatabaseFeatureUpgrade, which checks
-- information_schema before each ADD. The final target columns are:
-- ci_service.category_id
-- ci_service.payment_product_id_android / payment_product_id_ios
-- ci_service.sale_price_android / sale_price_ios
-- ci_order.client_platform
-- ci_payment.expected_order_type

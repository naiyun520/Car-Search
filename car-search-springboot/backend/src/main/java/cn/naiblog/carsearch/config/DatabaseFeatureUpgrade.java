package cn.naiblog.carsearch.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Small, additive schema upgrade for installations created before categories and
 * per-platform virtual-payment prices were introduced. Existing columns are kept
 * so old order snapshots and rollback deployments stay readable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseFeatureUpgrade implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public DatabaseFeatureUpgrade(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                create table if not exists ci_service_category(
                  id int unsigned not null auto_increment,
                  code varchar(40) not null,
                  name varchar(40) not null,
                  icon varchar(40) not null default 'grid',
                  sort int not null default 0,
                  status tinyint not null default 1,
                  created_at datetime not null,
                  updated_at datetime not null,
                  primary key(id), unique key uk_service_category_code(code),
                  key idx_service_category_status_sort(status,sort)
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        jdbc.execute("""
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
                ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                """);
        addColumn("ci_service", "category_id", "int unsigned null after provider_api_id");
        addColumn("ci_service", "payment_product_id_android", "varchar(128) not null default '' after payment_product_id");
        addColumn("ci_service", "payment_product_id_ios", "varchar(128) not null default '' after payment_product_id_android");
        addColumn("ci_service", "sale_price_android", "decimal(10,2) null after sale_price");
        addColumn("ci_service", "sale_price_ios", "decimal(10,2) null after sale_price_android");
        addColumn("ci_order", "client_platform", "varchar(16) not null default 'unknown' after cost_amount");
        addColumn("ci_payment", "expected_order_type", "tinyint null after pay_env");
        addColumn("ci_payment", "refund_attempts", "int unsigned not null default 0 after refund_status");
        addIndex("ci_service", "idx_service_category", "category_id,sort");
        addIndex("ci_order", "idx_order_status_created", "status,created_at");
        addIndex("ci_order", "idx_order_auto_refund", "status,client_platform,queried_at");
        addIndex("ci_order", "idx_order_user_id", "user_id,id");
        addIndex("ci_order", "idx_order_paid_at", "paid_at");
        addIndex("ci_payment", "idx_payment_refunded_at", "refunded_at");
        addIndex("ci_payment", "idx_payment_refund_checked", "refund_status,last_checked_at");

        jdbc.update("update ci_service set payment_product_id_android=payment_product_id where payment_product_id_android='' and payment_product_id<>''");
        jdbc.update("update ci_service set payment_product_id_ios=payment_product_id where payment_product_id_ios='' and payment_product_id<>''");
        jdbc.update("update ci_service set sale_price_android=sale_price where sale_price_android is null");
        jdbc.update("update ci_service set sale_price_ios=sale_price where sale_price_ios is null");
        jdbc.update("update ci_service set icon=case code when 'plate_basic' then 'builtin:plate' when 'vin_decode' then 'builtin:vin' when 'plate_full' then 'builtin:document' when 'etc_owner_verify' then 'builtin:etc' when 'plate_location' then 'builtin:location' when 'transfer_count' then 'builtin:transfer' when 'owned_vehicle_count' then 'builtin:owner' when 'owned_etc_count' then 'builtin:etc' when 'owner_verify_precise' then 'builtin:verified' when 'owner_verify' then 'builtin:shield' when 'insurance_dates' then 'builtin:insurance' when 'vin_plate' then 'builtin:search' else concat('builtin:',case when icon in ('vin','plate','verified','location','transfer','owner','etc','insurance','search','document','shield') then icon else 'car' end) end where icon='' or icon='car'");
        migrateEmailSettings();
        seedCategories();
    }

    private void migrateEmailSettings() {
        boolean masterMissing = !exists("select count(*) from ci_setting where `key`=?", "email_notify_enabled");
        seedSetting("email_notify_enabled", "0");
        seedSettingFromLegacy("email_event_payment_success", "email_notify_payment");
        seedSettingFromLegacy("email_event_query_success", "email_notify_query_success");
        seedSettingFromLegacy("email_event_query_failed", "email_notify_query_failed");
        seedSetting("email_event_refund_result", "0");
        seedSetting("email_event_provider_balance", "0");
        Integer enabled = jdbc.queryForObject("select count(*) from ci_setting where `key` in ('email_notify_payment','email_notify_query_success','email_notify_query_failed') and value='1'", Integer.class);
        if (masterMissing && enabled != null && enabled > 0)
            jdbc.update("update ci_setting set value='1' where `key`='email_notify_enabled' and value='0'");
    }

    private void seedSetting(String key, String value) {
        jdbc.update("insert ignore into ci_setting(`key`,value,updated_at) values(?,?,?)", key, value, LocalDateTime.now());
    }

    private void seedSettingFromLegacy(String key, String legacyKey) {
        jdbc.update("insert ignore into ci_setting(`key`,value,updated_at) select ?,coalesce((select value from ci_setting where `key`=? limit 1),'0'),?", key, legacyKey, LocalDateTime.now());
    }

    private void seedCategories() {
        LocalDateTime now = LocalDateTime.now();
        seed("vehicle", "车辆信息", "car", 10, now);
        seed("ownership", "车主与归属", "verified", 20, now);
        seed("insurance", "保险与记录", "shield", 30, now);
        seed("etc", "ETC服务", "road", 40, now);
        jdbc.update("update ci_service set category_id=(select id from ci_service_category where code='etc') where category_id is null and code like '%etc%'");
        jdbc.update("update ci_service set category_id=(select id from ci_service_category where code='ownership') where category_id is null and (code like '%owner%' or code like 'owned_%')");
        jdbc.update("update ci_service set category_id=(select id from ci_service_category where code='insurance') where category_id is null and (code like '%insurance%' or code like '%transfer%')");
        jdbc.update("update ci_service set category_id=(select id from ci_service_category where code='vehicle') where category_id is null");
    }

    private void seed(String code, String name, String icon, int sort, LocalDateTime now) {
        jdbc.update("insert ignore into ci_service_category(code,name,icon,sort,status,created_at,updated_at) values(?,?,?,?,1,?,?)",
                code, name, icon, sort, now, now);
    }

    private void addColumn(String table, String column, String definition) {
        if (!exists("select count(*) from information_schema.columns where table_schema=database() and table_name=? and column_name=?", table, column))
            jdbc.execute("alter table " + table + " add column " + column + " " + definition);
    }

    private void addIndex(String table, String index, String columns) {
        if (!exists("select count(*) from information_schema.statistics where table_schema=database() and table_name=? and index_name=?", table, index))
            jdbc.execute("alter table " + table + " add index " + index + "(" + columns + ")");
    }

    private boolean exists(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }
}

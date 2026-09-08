package cn.naiblog.carsearch.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fails startup with an actionable message instead of producing intermittent HTTP 500 errors on an old schema. */
@Component
public class DatabaseCompatibilityVerifier implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public DatabaseCompatibilityVerifier(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, List<String>> required = new LinkedHashMap<>();
        required.put("ci_admin", List.of("id", "username", "password_hash", "status", "must_change_password"));
        required.put("ci_admin_token", List.of("admin_id", "token_hash", "expires_at"));
        required.put("ci_admin_login_attempt", List.of("identity_hash", "fail_count", "locked_until"));
        required.put("ci_audit_log", List.of("admin_id", "action", "target_type", "target_id", "detail", "ip"));
        required.put("ci_setting", List.of("key", "value"));
        required.put("ci_service", List.of("code", "payment_product_id", "input_schema", "result_schema", "request_url_cipher"));
        required.put("ci_user", List.of("openid", "session_key_cipher", "status"));
        required.put("ci_user_token", List.of("user_id", "token_hash", "expires_at"));
        required.put("ci_order", List.of("order_no", "request_key", "input_cipher", "result_cipher", "service_snapshot_cipher", "status", "manual_refund_time"));
        required.put("ci_payment", List.of("order_id", "order_no", "wechat_order_type", "delivery_status", "refund_status", "refund_reason"));
        required.put("ci_feedback", List.of("user_id", "content", "status", "reply", "replied_at"));
        required.put("ci_announcement", List.of("title", "content", "suppress_hours", "status"));
        required.put("ci_wechat_event", List.of("event_key", "event_type", "request_cipher", "response_cipher", "process_status"));

        for (Map.Entry<String, List<String>> entry : required.entrySet()) {
            for (String column : entry.getValue()) {
                Integer found = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name=? and column_name=?",
                        Integer.class, entry.getKey(), column);
                if (found == null || found == 0) {
                    throw new IllegalStateException("数据库结构不兼容：缺少 " + entry.getKey() + "." + column + "。请先使用当前 PHP 版本的完整数据库结构升级后再启动 Spring Boot。");
                }
            }
        }
    }
}

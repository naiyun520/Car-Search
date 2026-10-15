package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AndroidAutoRefundService {
    private static final Logger log = LoggerFactory.getLogger(AndroidAutoRefundService.class);
    private final JdbcTemplate jdbc;
    private final SettingService settings;
    private final PaymentLifecycleService lifecycle;

    public AndroidAutoRefundService(JdbcTemplate jdbc, SettingService settings, PaymentLifecycleService lifecycle) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.lifecycle = lifecycle;
    }

    public int processPending(int requestedLimit) {
        if (!enabled()) return 0;
        int limit = Math.max(1, Math.min(100, requestedLimit));
        LocalDateTime enabledAt = enabledAt();
        LocalDateTime retryBefore = LocalDateTime.now().minusMinutes(10);
        List<Map<String,Object>> rows = jdbc.queryForList("""
                select o.order_no
                from ci_order o join ci_payment p on p.order_id=o.id
                where o.status='query_failed' and o.client_platform='android' and o.paid_at is not null
                  and o.queried_at>=? and coalesce(p.wechat_order_type,p.expected_order_type,0)<>7
                  and (p.refund_status='none' or (p.refund_status='failed' and p.refund_source='auto_android' and p.refund_attempts<3 and p.updated_at<?))
                order by o.queried_at asc limit ?
                """, enabledAt, retryBefore, limit);
        int started = 0;
        for (Map<String,Object> row : rows) {
            String orderNo = String.valueOf(row.get("order_no"));
            try {
                String state = lifecycle.requestAutomaticAndroidRefund(orderNo);
                if ("processing".equals(state) || "refunded".equals(state)) started++;
            } catch (Exception error) {
                log.warn("automatic Android refund not started order={} reason={}", orderNo, safe(error.getMessage()));
            }
        }
        return started;
    }

    public boolean enabled() {
        return "1".equals(settings.value("android_auto_refund_query_failed", "0"));
    }

    private LocalDateTime enabledAt() {
        try {
            return LocalDateTime.parse(settings.value("android_auto_refund_enabled_at", LocalDateTime.now().toString()));
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private String safe(String message) {
        String value = message == null ? "unknown" : message.replaceAll("[\\r\\n]+", " ");
        return value.length() <= 200 ? value : value.substring(0, 200);
    }
}

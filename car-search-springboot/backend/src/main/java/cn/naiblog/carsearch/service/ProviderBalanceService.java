package cn.naiblog.carsearch.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Provider balance snapshots. A disabled provider never performs a network request. */
@Service
public class ProviderBalanceService {
    private final JdbcTemplate jdbc;
    private final ProviderService provider;
    private final AtomicBoolean refreshing = new AtomicBoolean();

    public ProviderBalanceService(JdbcTemplate jdbc, ProviderService provider) {
        this.jdbc = jdbc;
        this.provider = provider;
    }

    @Scheduled(scheduler="balanceTaskScheduler",fixedDelayString = "${car.provider-balance-delay-ms:60000}", initialDelayString = "${car.provider-balance-initial-delay-ms:30000}")
    public void refreshDue() {
        if (!refreshing.compareAndSet(false, true)) return;
        try {
            for (Map<String, Object> row : jdbc.queryForList("""
                    select p.* from ci_provider p
                    where p.status=1 and p.balance_enabled=1 and p.balance_mode='independent_api'
                      and not exists (select 1 from ci_provider_balance_snapshot s where s.provider_id=p.id and s.observed_at>=timestampadd(minute,-greatest(p.balance_refresh_minutes,1),now()) and s.status='success')
                    order by p.id
                    """)) refresh(row, "scheduled");
        } finally { refreshing.set(false); }
    }

    public Map<String, Object> refreshNow(int providerId) {
        if (!refreshing.compareAndSet(false, true)) return Map.of("provider_id", providerId, "status", "refreshing");
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("select * from ci_provider where id=?", providerId);
            if (rows.isEmpty()) throw new cn.naiblog.carsearch.api.BusinessException("该供应商已不存在，请刷新供应商列表");
            Map<String,Object> row=rows.getFirst();
            if(number(row.get("status"))!=1||number(row.get("balance_enabled"))!=1||!"independent_api".equals(text(row.get("balance_mode"))))return refresh(row,"manual");
            List<Map<String, Object>> recent = jdbc.queryForList("select balance,status,observed_at from ci_provider_balance_snapshot where provider_id=? and source='manual' and observed_at>=? order by id desc limit 1", providerId, LocalDateTime.now().minusMinutes(1));
            if (!recent.isEmpty()) return Map.of("provider_id", providerId, "status", "rate_limited", "observed_at", recent.getFirst().get("observed_at"));
            return refresh(rows.getFirst(), "manual");
        } finally { refreshing.set(false); }
    }

    public List<Map<String, Object>> snapshots(int providerId, int limit) {
        return jdbc.queryForList("select id,provider_id,balance,source,status,error_code,error_message,observed_at from ci_provider_balance_snapshot where provider_id=? order by id desc limit ?", providerId, Math.max(1, Math.min(100, limit)));
    }

    private Map<String, Object> refresh(Map<String, Object> row, String source) {
        int providerId = number(row.get("id"));
        if (!"independent_api".equals(text(row.get("balance_mode"))))
            return Map.of("provider_id", providerId, "status", "unsupported", "message", "该供应商未配置余额接口，无法查询余额");
        if (number(row.get("status")) != 1 || number(row.get("balance_enabled")) != 1)
            return Map.of("provider_id", providerId, "status", "disabled", "message", "该供应商或余额查询已停用，未发送查询请求");
        LocalDateTime now = LocalDateTime.now();
        try {
            ProviderService.BalanceResult value = provider.queryBalance(row);
            jdbc.update("insert into ci_provider_balance_snapshot(provider_id,balance,source,status,observed_at,created_at) values(?,?,?,'success',?,?)",
                    providerId, value.balance(), source, now, now);
            return Map.of("provider_id", providerId, "status", "success", "balance", value.balance(), "observed_at", now);
        } catch (Exception error) {
            String code = error instanceof ProviderQueryException p ? p.providerCode() : "error";
            String message = error instanceof java.net.http.HttpTimeoutException||error instanceof java.net.SocketTimeoutException?"连接供应商余额接口超时，请稍后刷新；若持续超时，请检查接口地址与服务器网络":error instanceof java.net.ConnectException?"无法连接供应商余额接口，请检查接口地址和服务器网络":cut(error.getMessage(),500);
            jdbc.update("insert into ci_provider_balance_snapshot(provider_id,balance,source,status,error_code,error_message,observed_at,created_at) values(?,null,?,'failed',?,?,?,?)",
                    providerId, source, code, message, now, now);
            return Map.of("provider_id", providerId, "status", "failed", "error_code", code, "message", message, "observed_at", now);
        }
    }

    private static int number(Object value) { try { return value instanceof Number n ? n.intValue() : Integer.parseInt(text(value)); } catch (Exception e) { return 0; } }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static String cut(String value, int max) { String v = value == null ? "unknown" : value.replaceAll("[\\r\\n]+", " ").trim(); return v.length() <= max ? v : v.substring(0, max); }
}

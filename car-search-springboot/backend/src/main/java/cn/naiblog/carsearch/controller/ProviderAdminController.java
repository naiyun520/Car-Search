package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.*;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.service.AuditService;
import cn.naiblog.carsearch.service.ProviderBalanceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.naiblog.carsearch.db.GeneratedInsert;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/admin-api")
public class ProviderAdminController {
    private static final Set<String> AUTH_TYPES = Set.of("none", "sign_md5");
    private final JdbcTemplate jdbc;
    private final CryptoService crypto;
    private final AuditService audit;
    private final ProviderBalanceService balances;

    public ProviderAdminController(JdbcTemplate jdbc, CryptoService crypto, AuditService audit, ProviderBalanceService balances) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.audit = audit;
        this.balances = balances;
    }

    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select p.id,p.name,p.code,p.auth_type,p.config_cipher,p.base_url,p.allow_http,p.status,p.sort,p.balance_enabled,p.balance_mode,p.balance_api_path,p.balance_api_version,p.balance_value_path,p.balance_refresh_minutes,p.order_balance_tracking_enabled,p.updated_at,s.balance latest_balance,s.status latest_balance_status,s.error_message latest_balance_error,s.observed_at latest_balance_at from ci_provider p left join ci_provider_balance_snapshot s on s.id=(select x.id from ci_provider_balance_snapshot x where x.provider_id=p.id order by x.id desc limit 1) order by p.sort,p.id");
        rows.forEach(this::safe);
        return ApiResponse.ok(rows);
    }

    @GetMapping("/providers/encryption-health")
    public ApiResponse<Map<String, Object>> encryptionHealth() {
        List<String> ciphers = jdbc.query("select config_cipher from ci_provider where config_cipher is not null and config_cipher<>''",
                (rs, n) -> rs.getString(1));
        long readable = ciphers.stream().filter(crypto::canDecrypt).count();
        return ApiResponse.ok(Map.of(
                "key_fingerprint", crypto.keyFingerprint(),
                "encrypted_count", ciphers.size(),
                "decryptable_count", readable,
                "healthy", readable == ciphers.size()));
    }

    @GetMapping("/providers/{id}/balance")
    public ApiResponse<List<Map<String, Object>>> balanceHistory(@PathVariable int id) {
        find(id);
        return ApiResponse.ok(balances.snapshots(id, 30));
    }

    @PostMapping("/providers/{id}/balance/refresh")
    public ApiResponse<Map<String, Object>> refreshBalance(@PathVariable int id, HttpServletRequest req) {
        Map<String, Object> result = balances.refreshNow(id);
        audit.record(req, "provider.balance.refresh", "provider", String.valueOf(id), Map.of("status", result.get("status")));
        return ApiResponse.ok(result);
    }

    @PostMapping("/providers")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> b, HttpServletRequest req) {
        Validated v = validate(b, null);
        if (scalar("select count(*) from ci_provider where code=?", v.code) > 0)
            throw new BusinessException("供应商编码已存在");
        LocalDateTime now = LocalDateTime.now();
        int id=GeneratedInsert.insert(jdbc,"insert into ci_provider(name,code,auth_type,config_cipher,base_url,allow_http,status,sort,balance_enabled,balance_mode,balance_api_path,balance_api_version,balance_value_path,balance_refresh_minutes,order_balance_tracking_enabled,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                v.name, v.code, v.authType, crypto.encrypt(v.configMap), v.baseUrl, v.allowHttp ? 1 : 0, v.status,
                scalar("select coalesce(max(sort),0)+1 from ci_provider"), v.balanceEnabled ? 1 : 0, v.balanceMode,
                v.balanceApiPath, v.balanceApiVersion, v.balanceValuePath, v.balanceRefreshMinutes, v.orderBalanceTrackingEnabled ? 1 : 0, now, now);
        audit.record(req, "provider.create", "provider", String.valueOf(id), Map.of("code", v.code));
        return ApiResponse.ok(Map.of("id", id), "供应商已创建");
    }

    @PostMapping({"/providers/{id}", "/providers/{id}/update"})
    public ApiResponse<Map<String, Object>> update(@PathVariable int id, @RequestBody Map<String, Object> b, HttpServletRequest req) {
        Map<String, Object> old = find(id);
        Validated v = validate(b, old);
        jdbc.update("update ci_provider set name=?,code=?,auth_type=?,config_cipher=?,base_url=?,allow_http=?,status=?,balance_enabled=?,balance_mode=?,balance_api_path=?,balance_api_version=?,balance_value_path=?,balance_refresh_minutes=?,order_balance_tracking_enabled=?,updated_at=? where id=?",
                v.name, v.code, v.authType,
                v.configMap.isEmpty() ? old.get("config_cipher") : crypto.encrypt(v.configMap),
                v.baseUrl, v.allowHttp ? 1 : 0, v.status, v.balanceEnabled ? 1 : 0, v.balanceMode,
                v.balanceApiPath, v.balanceApiVersion, v.balanceValuePath, v.balanceRefreshMinutes, v.orderBalanceTrackingEnabled ? 1 : 0, LocalDateTime.now(), id);
        audit.record(req, "provider.update", "provider", String.valueOf(id), Map.of("fields", b.keySet()));
        return ApiResponse.ok(Map.of("id", id), "供应商已保存");
    }

    @DeleteMapping("/providers/{id}")
    public ApiResponse<Object> delete(@PathVariable int id, HttpServletRequest req) {
        Map<String, Object> p = find(id);
        if (scalar("select count(*) from ci_service where provider_api_id=?", id) > 0)
            throw new BusinessException("该供应商下有关联服务，不能删除，请先解除关联或改为禁用");
        jdbc.update("delete from ci_provider where id=?", id);
        audit.record(req, "provider.delete", "provider", String.valueOf(id), Map.of("code", p.get("code")));
        return ApiResponse.ok(null, "供应商已删除");
    }

    @PostMapping("/providers/sort")
    @Transactional
    public ApiResponse<Object> sort(@RequestBody Map<String, Object> b, HttpServletRequest req) {
        List<Integer> ids = b.get("ids") instanceof List<?> l
                ? l.stream().map(this::integer).filter(i -> i > 0).toList()
                : List.of();
        List<Integer> current = jdbc.queryForList("select id from ci_provider order by id", Integer.class);
        List<Integer> a = new ArrayList<>(ids), c = new ArrayList<>(current);
        Collections.sort(a);
        Collections.sort(c);
        if (ids.size() < 2 || !a.equals(c))
            throw new BusinessException("排序数据不完整，请刷新页面后重试");
        for (int i = 0; i < ids.size(); i++)
            jdbc.update("update ci_provider set sort=?,updated_at=? where id=?", i + 1, LocalDateTime.now(), ids.get(i));
        audit.record(req, "provider.sort", "provider", "", Map.of("ids", ids));
        return ApiResponse.ok(null, "排序已保存");
    }

    private Validated validate(Map<String, Object> b, Map<String, Object> old) {
        String code = old == null ? text(b.get("code")) : text(old.get("code"));
        String name = text(b.get("name"));
        String authType = text(b.getOrDefault("auth_type", "none"));
        String baseUrl = text(b.get("base_url")).trim();
        boolean allowHttp = "1".equals(text(b.get("allow_http"))) || "true".equals(text(b.get("allow_http")));
        int status = integer(b.getOrDefault("status", 1));
        boolean balanceEnabled = "1".equals(text(b.getOrDefault("balance_enabled", old == null ? 0 : old.get("balance_enabled")))) || "true".equalsIgnoreCase(text(b.getOrDefault("balance_enabled", old == null ? 0 : old.get("balance_enabled"))));
        String balanceMode = text(b.getOrDefault("balance_mode", old == null ? "unsupported" : old.get("balance_mode")));
        String balanceApiPath = text(b.getOrDefault("balance_api_path", old == null ? "" : old.get("balance_api_path")));
        String balanceApiVersion = text(b.getOrDefault("balance_api_version", old == null ? "" : old.get("balance_api_version")));
        String balanceValuePath = text(b.getOrDefault("balance_value_path", old == null ? "" : old.get("balance_value_path")));
        int balanceRefreshMinutes = integer(b.getOrDefault("balance_refresh_minutes", old == null ? 10 : old.get("balance_refresh_minutes")));
        boolean orderBalanceTrackingEnabled = "1".equals(text(b.getOrDefault("order_balance_tracking_enabled", old == null ? 0 : old.get("order_balance_tracking_enabled")))) || "true".equalsIgnoreCase(text(b.getOrDefault("order_balance_tracking_enabled", old == null ? 0 : old.get("order_balance_tracking_enabled"))));

        if (!code.matches("[a-z][a-z0-9_]{2,39}"))
            throw new BusinessException("供应商编码格式不正确（小写字母开头，仅限小写字母、数字和下划线）");
        if (name.isBlank() || name.length() > 80)
            throw new BusinessException("供应商名称不能为空且不超过80字");
        if (!AUTH_TYPES.contains(authType))
            throw new BusinessException("供应商认证方式：请选择无认证或签名认证");
        if (!Set.of(0, 1).contains(status))
            throw new BusinessException("供应商状态：请选择启用或停用");

        // base_url 验证
        if (baseUrl.isBlank() && old == null)
            throw new BusinessException("请填写基础 URL");
        if (!Set.of("unsupported", "independent_api").contains(balanceMode))
            throw new BusinessException("余额查询模式：请选择不支持或独立接口");
        if (balanceEnabled && !"independent_api".equals(balanceMode))
            throw new BusinessException("启用余额查询前必须配置独立余额接口");
        if (balanceEnabled && balanceApiPath.isBlank())throw new BusinessException("余额接口路径：启用余额查询时必须填写，例如 /InterfaceClient/getBalance");
        if (balanceEnabled && balanceValuePath.isBlank())throw new BusinessException("余额字段路径：启用余额查询时必须填写，例如 result.balance");
        if(orderBalanceTrackingEnabled&&!balanceEnabled)throw new BusinessException("启用订单余额跟踪前，必须先启用供应商余额查询");
        if(balanceApiPath.length()>500)throw new BusinessException("余额接口路径：不能超过500个字符");
        if(balanceApiVersion.length()>20)throw new BusinessException("余额接口版本：不能超过20个字符");
        if(balanceValuePath.length()>100)throw new BusinessException("余额字段路径：不能超过100个字符");
        if(balanceRefreshMinutes<1||balanceRefreshMinutes>1440)throw new BusinessException("余额刷新间隔：请填写1至1440分钟");

        if (!baseUrl.isBlank()) {
            try {
                URI uri = URI.create(baseUrl);
                String scheme = uri.getScheme();
                if (scheme == null || uri.getHost() == null)
                    throw new Exception();
                if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                    throw new Exception();
                if ("http".equalsIgnoreCase(scheme) && !allowHttp)
                    throw new BusinessException("HTTP 协议需要勾选「允许 HTTP」");
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException("基础 URL 格式不正确");
            }
        }

        // config_cipher: appid, secret, version
        String appid = text(b.get("appid"));
        String secret = text(b.get("secret"));
        String version = text(b.getOrDefault("version", "1.0.0"));
        Map<String, Object> configMap = Map.of();
        if (!appid.isBlank() || !secret.isBlank()) {
            if (appid.isBlank() || secret.isBlank())
                throw new BusinessException("AppID 和 Secret 必须同时填写");
            configMap = new LinkedHashMap<>();
            configMap.put("appid", appid);
            configMap.put("secret", secret);
            configMap.put("version", version.isBlank() ? "1.0.0" : version);
        } else if (old == null && "sign_md5".equals(authType)) {
            throw new BusinessException("签名认证必须填写 AppID 和 Secret");
        }

        return new Validated(code, name, authType, baseUrl, allowHttp, status, configMap, balanceEnabled, balanceMode, balanceApiPath, balanceApiVersion, balanceValuePath, balanceRefreshMinutes, orderBalanceTrackingEnabled);
    }

    private Map<String, Object> find(int id) {
        List<Map<String, Object>> r = jdbc.queryForList("select * from ci_provider where id=?", id);
        if (r.isEmpty()) throw new BusinessException(404, HttpStatus.NOT_FOUND, "供应商不存在");
        return r.getFirst();
    }

    private void safe(Map<String, Object> r) {
        String cipher = text(r.get("config_cipher"));
        Map<String, Object> d = crypto.decrypt(cipher);
        r.remove("config_cipher");
        r.put("appid_configured", !text(d.get("appid")).isBlank());
        r.put("secret_configured", !text(d.get("secret")).isBlank());
        r.put("version", d.getOrDefault("version", "1.0.0"));
    }

    private long scalar(String s, Object... a) {
        Long v = jdbc.queryForObject(s, Long.class, a);
        return v == null ? 0 : v;
    }

    private int integer(Object v) {
        try { return v instanceof Number n ? n.intValue() : Integer.parseInt(text(v)); } catch (Exception e) { return 0; }
    }

    private String text(Object v) { return v == null ? "" : String.valueOf(v).trim(); }

    private record Validated(String code, String name, String authType, String baseUrl, boolean allowHttp, int status, Map<String, Object> configMap,
                             boolean balanceEnabled, String balanceMode, String balanceApiPath, String balanceApiVersion,
                             String balanceValuePath, int balanceRefreshMinutes, boolean orderBalanceTrackingEnabled) {}
}

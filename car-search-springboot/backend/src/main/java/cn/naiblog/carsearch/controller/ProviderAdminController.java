package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.*;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
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

    public ProviderAdminController(JdbcTemplate jdbc, CryptoService crypto, AuditService audit) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.audit = audit;
    }

    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id,name,code,auth_type,config_cipher,base_url,allow_http,status,sort,updated_at from ci_provider order by sort,id");
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

    @PostMapping("/providers")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> b, HttpServletRequest req) {
        Validated v = validate(b, null);
        if (scalar("select count(*) from ci_provider where code=?", v.code) > 0)
            throw new BusinessException("供应商编码已存在");
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("insert into ci_provider(name,code,auth_type,config_cipher,base_url,allow_http,status,sort,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?)",
                v.name, v.code, v.authType, crypto.encrypt(v.configMap), v.baseUrl, v.allowHttp ? 1 : 0, v.status,
                scalar("select coalesce(max(sort),0)+1 from ci_provider"), now, now);
        int id = jdbc.queryForObject("select last_insert_id()", Integer.class);
        audit.record(req, "provider.create", "provider", String.valueOf(id), Map.of("code", v.code));
        return ApiResponse.ok(Map.of("id", id), "供应商已创建");
    }

    @PostMapping({"/providers/{id}", "/providers/{id}/update"})
    public ApiResponse<Map<String, Object>> update(@PathVariable int id, @RequestBody Map<String, Object> b, HttpServletRequest req) {
        Map<String, Object> old = find(id);
        Validated v = validate(b, old);
        jdbc.update("update ci_provider set name=?,code=?,auth_type=?,config_cipher=?,base_url=?,allow_http=?,status=?,updated_at=? where id=?",
                v.name, v.code, v.authType,
                v.configMap.isEmpty() ? old.get("config_cipher") : crypto.encrypt(v.configMap),
                v.baseUrl, v.allowHttp ? 1 : 0, v.status, LocalDateTime.now(), id);
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

        if (!code.matches("[a-z][a-z0-9_]{2,39}"))
            throw new BusinessException("供应商编码格式不正确（小写字母开头，仅限小写字母、数字和下划线）");
        if (name.isBlank() || name.length() > 80)
            throw new BusinessException("供应商名称不能为空且不超过80字");
        if (!AUTH_TYPES.contains(authType))
            throw new BusinessException("认证类型无效");
        if (!Set.of(0, 1).contains(status))
            throw new BusinessException("状态值无效");

        // base_url 验证
        if (baseUrl.isBlank() && old == null)
            throw new BusinessException("请填写基础 URL");
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

        return new Validated(code, name, authType, baseUrl, allowHttp, status, configMap);
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

    private record Validated(String code, String name, String authType, String baseUrl, boolean allowHttp, int status, Map<String, Object> configMap) {}
}
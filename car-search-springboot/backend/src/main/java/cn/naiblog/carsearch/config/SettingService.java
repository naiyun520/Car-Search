package cn.naiblog.carsearch.config;

import cn.naiblog.carsearch.security.CryptoService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.time.LocalDateTime;

@Service
public class SettingService {
    private static final Logger log = LoggerFactory.getLogger(SettingService.class);
    private final JdbcTemplate jdbc;
    private final CryptoService crypto;
    private final CarProperties properties;

    public SettingService(JdbcTemplate jdbc, CryptoService crypto, CarProperties properties) {
        this.jdbc = jdbc; this.crypto = crypto; this.properties = properties;
    }

    public String value(String key, String fallback) {
        return jdbc.query("select value from ci_setting where `key`=?", rs -> rs.next() ? rs.getString(1) : fallback, key);
    }

    public String secure(String key, String fallback) {
        String cipher = jdbc.query("select value_cipher from ci_secure_setting where `key`=?", rs -> rs.next() ? rs.getString(1) : null, key);
        if (cipher == null || cipher.isBlank()) return fallback == null ? "" : fallback;
        Map<String, Object> decoded = crypto.decrypt(cipher);
        if (!decoded.containsKey("value")) {
            log.warn("secure setting cannot be decrypted key={}; use configured environment fallback", key);
            return fallback == null ? "" : fallback;
        }
        return String.valueOf(decoded.get("value"));
    }

    public void saveValue(String key, String value) {
        jdbc.update("insert into ci_setting(`key`,value,updated_at) values(?,?,?) on duplicate key update value=values(value),updated_at=values(updated_at)", key, value, LocalDateTime.now());
    }

    public void saveSecure(String key, String value) {
        jdbc.update("insert into ci_secure_setting(`key`,value_cipher,updated_at) values(?,?,?) on duplicate key update value_cipher=values(value_cipher),updated_at=values(updated_at)", key, crypto.encrypt(Map.of("value", value)), LocalDateTime.now());
    }

    public WechatConfig wechat() {
        String appIdFallback = properties.wechat() == null ? "" : properties.wechat().appId();
        String secretFallback = properties.wechat() == null ? "" : properties.wechat().appSecret();
        return new WechatConfig(value("wechat_app_id", appIdFallback), secure("wechat_app_secret", secretFallback),
                value("wechat_offer_id", ""), secure("wechat_sandbox_app_key", ""), secure("wechat_production_app_key", ""),
                Integer.parseInt(value("wechat_pay_env", "0")));
    }

    public record WechatConfig(String appId, String appSecret, String offerId, String sandboxAppKey,
                               String productionAppKey, int payEnv) {
        public String appKey() { return payEnv == 1 ? sandboxAppKey : productionAppKey; }
        public WechatConfig withPayEnv(int env) { return new WechatConfig(appId, appSecret, offerId, sandboxAppKey, productionAppKey, env); }
    }
}

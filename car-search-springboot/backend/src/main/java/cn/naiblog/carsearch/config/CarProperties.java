package cn.naiblog.carsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "car")
public record CarProperties(String clientProtocol, String dataEncryptKey, boolean devLoginEnabled, Wechat wechat) {
    public record Wechat(String appId, String appSecret) {}
}

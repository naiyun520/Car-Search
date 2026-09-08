package cn.naiblog.carsearch.wechat;

import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.config.SettingService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class WechatLoginService {
    private final SettingService settings;
    private final RestClient client = RestClient.builder().baseUrl("https://api.weixin.qq.com").build();
    public WechatLoginService(SettingService settings) { this.settings = settings; }

    public Session codeToSession(String code) {
        SettingService.WechatConfig config = settings.wechat();
        if (config.appId().isBlank() || config.appSecret().isBlank())
            throw new BusinessException(503, HttpStatus.SERVICE_UNAVAILABLE, "微信登录暂未配置");
        Session result = client.get().uri(uri -> uri.path("/sns/jscode2session")
                .queryParam("appid", config.appId()).queryParam("secret", config.appSecret())
                .queryParam("js_code", code).queryParam("grant_type", "authorization_code").build())
                .retrieve().body(Session.class);
        if (result == null || result.errcode() != 0 || result.openid() == null || result.openid().isBlank() || result.sessionKey() == null)
            throw new BusinessException(503, HttpStatus.SERVICE_UNAVAILABLE, "登录失败，请稍后重试");
        return result;
    }

    public record Session(String openid, String unionid, @JsonProperty("session_key") String sessionKey,
                          @JsonProperty("errcode") int errcode, @JsonProperty("errmsg") String errmsg) {}
}

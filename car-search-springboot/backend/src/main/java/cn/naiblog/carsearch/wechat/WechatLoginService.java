package cn.naiblog.carsearch.wechat;

import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.config.SettingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Service
public class WechatLoginService {
    private static final Logger log = LoggerFactory.getLogger(WechatLoginService.class);
    private final SettingService settings;
    private final ObjectMapper mapper;
    private final RestClient client;

    public WechatLoginService(SettingService settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder().baseUrl("https://api.weixin.qq.com").requestFactory(requestFactory).build();
    }

    public Session codeToSession(String code) {
        SettingService.WechatConfig config = settings.wechat();
        if (config.appId().isBlank() || config.appSecret().isBlank())
            throw new BusinessException(503, HttpStatus.SERVICE_UNAVAILABLE, "微信登录暂未配置");
        try {
            // 微信偶尔会以 text/plain 返回 JSON，因此先按文本读取，再显式解析。
            String body = client.get().uri(uri -> uri.path("/sns/jscode2session")
                    .queryParam("appid", config.appId()).queryParam("secret", config.appSecret())
                    .queryParam("js_code", code).queryParam("grant_type", "authorization_code").build())
                    .retrieve().body(String.class);
            Map<?, ?> payload = mapper.readValue(body == null ? "{}" : body, Map.class);
            int errcode = number(payload.get("errcode"));
            String errmsg = text(payload.get("errmsg"));
            Session result = new Session(text(payload.get("openid")), text(payload.get("unionid")),
                    text(payload.get("session_key")), errcode, errmsg);
            if (errcode != 0 || result.openid().isBlank() || result.sessionKey().isBlank()) {
                log.warn("WeChat code2Session rejected request errcode={}", errcode);
                throw new BusinessException(503, HttpStatus.SERVICE_UNAVAILABLE,
                        errcode == 40029 ? "登录凭证已失效，请重新进入小程序" : "微信登录校验失败，请检查 AppID、AppSecret 和服务器网络");
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("WeChat code2Session network failure: {}", exception.getClass().getSimpleName());
            throw new BusinessException(503, HttpStatus.SERVICE_UNAVAILABLE, "微信登录服务连接失败，请稍后重试");
        } catch (Exception exception) {
            log.warn("WeChat code2Session returned an unreadable response: {}", exception.getClass().getSimpleName());
            throw new BusinessException(503, HttpStatus.SERVICE_UNAVAILABLE, "微信登录服务返回异常，请检查服务器网络");
        }
    }

    public Session localDevelopmentSession() {
        String sessionKey = Base64.getEncoder().encodeToString("local-dev-key-01".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new Session("local-dev-openid", "", sessionKey, 0, "local development");
    }

    private static int number(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }

    public record Session(String openid, String unionid, String sessionKey, int errcode, String errmsg) {}
}

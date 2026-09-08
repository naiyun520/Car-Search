package cn.naiblog.carsearch.api;

import cn.naiblog.carsearch.config.CarProperties;
import cn.naiblog.carsearch.security.CurrentUser;
import cn.naiblog.carsearch.security.UserAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ApiSupport {
    private final CarProperties properties;
    public ApiSupport(CarProperties properties) { this.properties = properties; }
    public void requireProtocol(HttpServletRequest request) {
        String version = request.getHeader("X-Car-Client-Version");
        if (version == null || !constantEquals(properties.clientProtocol(), version.trim()))
            throw new BusinessException("客户端版本已过期，请完全退出并重新打开最新版小程序");
    }
    public CurrentUser user(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(UserAuthInterceptor.USER_ATTRIBUTE);
    }
    private boolean constantEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}

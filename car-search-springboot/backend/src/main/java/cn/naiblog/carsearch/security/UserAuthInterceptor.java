package cn.naiblog.carsearch.security;

import cn.naiblog.carsearch.api.ApiResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class UserAuthInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(UserAuthInterceptor.class);
    public static final String USER_ATTRIBUTE = CurrentUser.class.getName();
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public UserAuthInterceptor(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String authorization = request.getHeader("Authorization");
        String tokenSource = "header";
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).trim() : "";
        // 兼容 uni.uploadFile 不发送 header 的情况：从 URL 查询参数获取 token
        if (token.isEmpty()) {
            String qs = request.getQueryString();
            if (qs != null) {
                for (String param : qs.split("&")) {
                    if (param.startsWith("access_token=")) {
                        try {
                            token = java.net.URLDecoder.decode(param.substring(13), java.nio.charset.StandardCharsets.UTF_8).trim();
                            tokenSource = "query";
                        } catch (Exception e) {
                            log.warn("[Auth] URL参数token解码失败 uri={}", uri, e);
                        }
                        break;
                    }
                }
            }
        }
        if (token.isEmpty()) {
            log.warn("[Auth] 无token uri={}, hasAuthHeader={}", uri, authorization != null);
        }
        List<CurrentUser> users = token.isEmpty() ? List.of() : jdbc.query("""
                select u.id,u.openid,u.unionid,u.nickname,u.avatar_url,u.phone
                from ci_user_token t join ci_user u on u.id=t.user_id
                where t.token_hash=? and t.expires_at>? and t.created_at>=? and u.status=1
                  and u.session_key_cipher is not null and u.session_key_cipher<>'' limit 1
                """, (rs, row) -> new CurrentUser(rs.getLong("id"), rs.getString("openid"), rs.getString("unionid"),
                rs.getString("nickname"), rs.getString("avatar_url"), rs.getString("phone")), Hashing.sha256(token),
                LocalDateTime.now(), LocalDateTime.now().minusHours(2));
        if (!users.isEmpty()) {
            log.debug("[Auth] 认证通过 uri={}, userId={}, tokenSource={}", uri, users.getFirst().id(), tokenSource);
            request.setAttribute(USER_ATTRIBUTE, users.getFirst());
            return true;
        }
        if (!token.isEmpty()) {
            log.warn("[Auth] token无效或已过期 uri={}, tokenSource={}, tokenPrefix={}", uri, tokenSource, token.substring(0, Math.min(8, token.length())));
        }
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiResponse.error(401, "请先登录"));
        return false;
    }
}

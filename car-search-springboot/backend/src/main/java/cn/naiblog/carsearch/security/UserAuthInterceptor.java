package cn.naiblog.carsearch.security;

import cn.naiblog.carsearch.api.ApiResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class UserAuthInterceptor implements HandlerInterceptor {
    public static final String USER_ATTRIBUTE = CurrentUser.class.getName();
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public UserAuthInterceptor(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).trim() : "";
        List<CurrentUser> users = token.isEmpty() ? List.of() : jdbc.query("""
                select u.id,u.openid,u.unionid,u.nickname,u.avatar_url,u.phone
                from ci_user_token t join ci_user u on u.id=t.user_id
                where t.token_hash=? and t.expires_at>? and t.created_at>=? and u.status=1
                  and u.session_key_cipher is not null and u.session_key_cipher<>'' limit 1
                """, (rs, row) -> new CurrentUser(rs.getLong("id"), rs.getString("openid"), rs.getString("unionid"),
                rs.getString("nickname"), rs.getString("avatar_url"), rs.getString("phone")), Hashing.sha256(token),
                LocalDateTime.now(), LocalDateTime.now().minusHours(2));
        if (!users.isEmpty()) {
            request.setAttribute(USER_ATTRIBUTE, users.getFirst());
            return true;
        }
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiResponse.error(401, "请先登录"));
        return false;
    }
}

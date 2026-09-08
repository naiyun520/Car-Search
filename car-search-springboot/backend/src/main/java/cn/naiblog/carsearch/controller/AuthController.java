package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.ApiSupport;
import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.security.CurrentUser;
import cn.naiblog.carsearch.security.Hashing;
import cn.naiblog.carsearch.wechat.WechatLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final JdbcTemplate jdbc; private final ApiSupport api; private final CryptoService crypto; private final WechatLoginService wechat;
    private final SecureRandom random = new SecureRandom();
    public AuthController(JdbcTemplate jdbc, ApiSupport api, CryptoService crypto, WechatLoginService wechat) {
        this.jdbc = jdbc; this.api = api; this.crypto = crypto; this.wechat = wechat;
    }

    @PostMapping("/auth/login") @Transactional
    public ApiResponse<Map<String,Object>> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        api.requireProtocol(request);
        WechatLoginService.Session session = wechat.codeToSession(body.code().trim());
        LocalDateTime now = LocalDateTime.now();
        List<Map<String,Object>> rows = jdbc.queryForList("select * from ci_user where openid=? limit 1", session.openid());
        long userId;
        if (rows.isEmpty()) {
            jdbc.update("insert into ci_user(openid,unionid,nickname,avatar_url,phone,session_key_cipher,status,last_login_at,created_at,updated_at) values(?,?,\'微信用户\',\'\',\'\',?,1,?,?,?)",
                    session.openid(), session.unionid(), crypto.encrypt(Map.of("value",session.sessionKey())), now, now, now);
            userId = jdbc.queryForObject("select id from ci_user where openid=?", Long.class, session.openid());
        } else {
            userId = ((Number) rows.getFirst().get("id")).longValue();
            jdbc.update("update ci_user set unionid=coalesce(?,unionid),session_key_cipher=?,last_login_at=?,updated_at=? where id=?",
                    session.unionid(), crypto.encrypt(Map.of("value",session.sessionKey())), now, now, userId);
        }
        byte[] tokenBytes = new byte[32]; random.nextBytes(tokenBytes); String token = HexFormat.of().formatHex(tokenBytes);
        jdbc.update("insert into ci_user_token(user_id,token_hash,expires_at,created_at) values(?,?,?,?)",
                userId, Hashing.sha256(token), now.plusHours(2), now);
        Map<String,Object> fresh = jdbc.queryForMap("select id,nickname,avatar_url,phone from ci_user where id=?", userId);
        return ApiResponse.ok(Map.of("token",token,"user",safeUser(fresh)));
    }

    @GetMapping("/me") public ApiResponse<Map<String,Object>> me(HttpServletRequest request) {
        CurrentUser user = api.user(request); return ApiResponse.ok(safeUser(Map.of("id",user.id(),"nickname",user.nickname(),"avatar_url",user.avatarUrl(),"phone",user.phone())));
    }

    @PostMapping("/me/profile")
    public ApiResponse<Map<String,Object>> updateProfile(@RequestBody ProfileRequest body, HttpServletRequest request) {
        CurrentUser user = api.user(request);
        String nickname = trimToLength(body.nickname(), 64);
        if (nickname.isBlank()) nickname = "微信用户";
        String avatarUrl = trimToLength(body.avatarUrl(), 500);
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("update ci_user set nickname=?,avatar_url=?,updated_at=? where id=?", nickname, avatarUrl, now, user.id());
        Map<String,Object> fresh = jdbc.queryForMap("select id,nickname,avatar_url,phone from ci_user where id=?", user.id());
        return ApiResponse.ok(safeUser(fresh), "资料已更新");
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Object> logout(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7))
            jdbc.update("delete from ci_user_token where token_hash=?", Hashing.sha256(authorization.substring(7).trim()));
        return ApiResponse.ok(null, "已安全退出");
    }

    private String trimToLength(String value, int length) {
        String text = value == null ? "" : value.trim();
        return text.codePointCount(0, text.length()) <= length ? text : text.substring(0, text.offsetByCodePoints(0, length));
    }

    private Map<String,Object> safeUser(Map<String,Object> row) {
        String phone = String.valueOf(row.getOrDefault("phone", ""));
        Map<String,Object> safe = new LinkedHashMap<>(); safe.put("id",row.get("id")); safe.put("nickname",row.get("nickname")); safe.put("avatar_url",row.get("avatar_url"));
        safe.put("phone_masked",phone.length() >= 7 ? phone.substring(0,3)+"****"+phone.substring(phone.length()-4) : ""); return safe;
    }
    public record LoginRequest(@NotBlank String code) {}
    public record ProfileRequest(String nickname, @com.fasterxml.jackson.annotation.JsonProperty("avatar_url") String avatarUrl) {}
}

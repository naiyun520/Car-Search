package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.ApiSupport;
import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.config.CarProperties;
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
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final JdbcTemplate jdbc; private final ApiSupport api; private final CryptoService crypto; private final WechatLoginService wechat; private final CarProperties properties;
    private final SecureRandom random = new SecureRandom();
    public AuthController(JdbcTemplate jdbc, ApiSupport api, CryptoService crypto, WechatLoginService wechat, CarProperties properties) {
        this.jdbc = jdbc; this.api = api; this.crypto = crypto; this.wechat = wechat; this.properties = properties;
    }

    @PostMapping("/auth/login") @Transactional
    public ApiResponse<Map<String,Object>> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        api.requireProtocol(request);
        WechatLoginService.Session session = localDevelopmentLoginAllowed(request)
                ? wechat.localDevelopmentSession()
                : wechat.codeToSession(body.code().trim());
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

    /**
     * 注销账号：有进行中订单（已支付/查询中/待核对/退款中）时拒绝；
     * 否则匿名化资料（昵称/头像/手机号/unionid/session_key）、openid 置墓碑值解绑、status=0 作废登录态并删除全部 token。
     * 注销后同一微信下次登录将创建全新账号，历史订单归属旧账号不可再查看。
     */
    @PostMapping("/auth/deactivate") @Transactional
    public ApiResponse<Object> deactivate(HttpServletRequest request) {
        CurrentUser user = api.user(request);
        List<Map<String, Object>> active = jdbc.queryForList(
                "select order_no from ci_order where user_id=? and status in ('paid','querying','payment_review','refunding') limit 1",
                user.id());
        if (!active.isEmpty()) throw new BusinessException("存在进行中的订单，完成或退款后才能注销账号");
        LocalDateTime now = LocalDateTime.now();
        // openid 为 NOT NULL + 唯一键：用墓碑值解绑（id 保证唯一），下次微信登录走新建账号
        String tombstone = "deleted_" + user.id() + "_" + System.currentTimeMillis();
        jdbc.update("update ci_user set openid=?,unionid=NULL,nickname='已注销用户',avatar_url='',phone='',"
                + "session_key_cipher=NULL,status=0,updated_at=? where id=?", tombstone, now, user.id());
        jdbc.update("delete from ci_user_token where user_id=?", user.id());
        return ApiResponse.ok(null, "账号已注销");
    }

    private String trimToLength(String value, int length) {
        String text = value == null ? "" : value.trim();
        return text.codePointCount(0, text.length()) <= length ? text : text.substring(0, text.offsetByCodePoints(0, length));
    }

    boolean localDevelopmentLoginAllowed(HttpServletRequest request) {
        if (!properties.devLoginEnabled()) return false;
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    private Map<String,Object> safeUser(Map<String,Object> row) {
        String phone = String.valueOf(row.getOrDefault("phone", ""));
        Map<String,Object> safe = new LinkedHashMap<>(); safe.put("id",row.get("id")); safe.put("nickname",row.get("nickname")); safe.put("avatar_url",row.get("avatar_url"));
        safe.put("phone_masked",phone.length() >= 7 ? phone.substring(0,3)+"****"+phone.substring(phone.length()-4) : ""); return safe;
    }
    public record LoginRequest(@NotBlank String code) {}
    public record ProfileRequest(String nickname, String avatar_url) {
        public String avatarUrl() { return avatar_url; }
    }
}

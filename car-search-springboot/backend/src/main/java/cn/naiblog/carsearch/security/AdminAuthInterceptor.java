package cn.naiblog.carsearch.security;

import cn.naiblog.carsearch.api.BusinessException;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.time.LocalDateTime;import java.util.*;

@Component public class AdminAuthInterceptor implements HandlerInterceptor {
    public static final String ATTRIBUTE="currentAdmin";private final JdbcTemplate jdbc;public AdminAuthInterceptor(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object h){String token=Optional.ofNullable(req.getHeader("Authorization")).orElse("").replaceFirst("(?i)^Bearer\\s+","");List<Map<String,Object>>rows=token.isBlank()?List.of():jdbc.queryForList("select a.id,a.username,a.must_change_password from ci_admin_token t join ci_admin a on a.id=t.admin_id where t.token_hash=? and t.expires_at>? and a.status=1 limit 1",Hashing.sha256(token),LocalDateTime.now());if(rows.isEmpty())throw new BusinessException(401,HttpStatus.UNAUTHORIZED,"管理端登录已失效");Map<String,Object>a=rows.getFirst();boolean must=((Number)a.get("must_change_password")).intValue()!=0;if(must&&!req.getRequestURI().equals("/admin-api/password"))throw new BusinessException(428,HttpStatus.PRECONDITION_REQUIRED,"首次登录必须先修改默认密码");req.setAttribute(ATTRIBUTE,new CurrentAdmin(((Number)a.get("id")).intValue(),String.valueOf(a.get("username")),must));res.setHeader("Cache-Control","no-store, no-cache, must-revalidate, private");res.setHeader("X-Content-Type-Options","nosniff");return true;}
}

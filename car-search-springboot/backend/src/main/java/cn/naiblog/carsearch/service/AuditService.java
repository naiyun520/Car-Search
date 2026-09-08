package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.security.AdminAuthInterceptor;
import cn.naiblog.carsearch.security.CurrentAdmin;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public void record(HttpServletRequest request, String action, String targetType, String targetId, Object detail) {
        try {
            CurrentAdmin admin = (CurrentAdmin) request.getAttribute(AdminAuthInterceptor.ATTRIBUTE);
            if (admin == null) return;
            String ip = request.getRemoteAddr();
            if (request.getHeader("X-Forwarded-For") != null) ip = request.getHeader("X-Forwarded-For").split(",", 2)[0].trim();
            jdbc.update("insert into ci_audit_log(admin_id,action,target_type,target_id,detail,ip,created_at) values(?,?,?,?,?,?,?)",
                    admin.id(), action, targetType, cut(targetId, 64), mapper.writeValueAsString(detail), cut(ip, 45), LocalDateTime.now());
        } catch (Exception error) {
            log.error("audit log failed action={}", action, error);
        }
    }

    private String cut(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}

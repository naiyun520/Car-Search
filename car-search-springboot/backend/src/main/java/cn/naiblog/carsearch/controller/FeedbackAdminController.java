package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.api.Pagination;
import cn.naiblog.carsearch.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/admin-api")
public class FeedbackAdminController {
    private static final String SELECT = "select f.id,f.user_id,f.type,f.content,f.contact,f.status,f.reply," +
            "f.replied_at,f.created_at,f.updated_at,u.nickname from ci_feedback f " +
            "left join ci_user u on u.id=f.user_id ";
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public FeedbackAdminController(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/feedback")
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "1") int page) {
        page = Math.max(1, page);
        int pageSize = 20;
        Long total = jdbc.queryForObject("select count(*) from ci_feedback", Long.class);
        List<Map<String, Object>> rows = jdbc.queryForList(
                SELECT + "order by f.id desc limit ? offset ?", pageSize, (page - 1) * pageSize);
        return ApiResponse.ok(Pagination.of(rows, total == null ? 0 : total, page, pageSize));
    }

    @GetMapping("/feedback-detail/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id) {
        return ApiResponse.ok(find(id));
    }

    @PostMapping("/feedback/{id}/reply")
    public ApiResponse<Object> reply(@PathVariable long id, @RequestBody Map<String, Object> body,
                                     HttpServletRequest request) {
        find(id);
        String reply = text(body.get("reply"));
        if (reply.isBlank() || reply.codePointCount(0, reply.length()) > 1000) {
            throw new BusinessException("回复内容不能为空且不能超过1000字");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("update ci_feedback set reply=?,status='resolved',replied_at=?,updated_at=? where id=?",
                reply, now, now, id);
        audit.record(request, "feedback.reply", "feedback", String.valueOf(id), Map.of("status", "resolved"));
        return ApiResponse.ok(null, "回复已发送，用户可在投诉意见中查看");
    }

    @DeleteMapping("/feedback/{id}")
    public ApiResponse<Object> delete(@PathVariable long id, HttpServletRequest request) {
        Map<String, Object> feedback = find(id);
        jdbc.update("delete from ci_feedback where id=?", id);
        audit.record(request, "feedback.delete", "feedback", String.valueOf(id),
                Map.of("user_id", feedback.get("user_id")));
        return ApiResponse.ok(null, "反馈已删除");
    }

    @DeleteMapping("/feedback/batch")
    @Transactional
    public ApiResponse<Map<String, Object>> batch(@RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        List<Long> ids = new ArrayList<>();
        if (body.get("ids") instanceof List<?> raw) {
            for (Object value : raw) {
                long id = positiveLong(value);
                if (id > 0) ids.add(id);
            }
        }
        ids = new ArrayList<>(new LinkedHashSet<>(ids));
        if (ids.isEmpty()) throw new BusinessException("请选择需要删除的反馈");
        if (ids.size() > 100) throw new BusinessException("单次最多删除100条反馈");
        String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Long> existing = jdbc.queryForList(
                "select id from ci_feedback where id in (" + marks + ")", Long.class, ids.toArray());
        if (existing.isEmpty()) throw new BusinessException("所选反馈不存在");
        String existingMarks = String.join(",", Collections.nCopies(existing.size(), "?"));
        int deleted = jdbc.update("delete from ci_feedback where id in (" + existingMarks + ")", existing.toArray());
        audit.record(request, "feedback.batch_delete", "feedback", join(existing), Map.of("count", deleted));
        return ApiResponse.ok(Map.of("deleted", deleted), "已删除" + deleted + "条反馈");
    }

    private Map<String, Object> find(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT + "where f.id=?", id);
        if (rows.isEmpty()) throw new BusinessException(404, HttpStatus.NOT_FOUND, "反馈不存在");
        return rows.getFirst();
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private long positiveLong(Object value) {
        try { return value instanceof Number n ? n.longValue() : Long.parseLong(text(value)); }
        catch (Exception ignored) { return 0; }
    }
    private String join(List<Long> values) {
        return values.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    }
}

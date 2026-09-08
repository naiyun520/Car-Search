package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.ApiSupport;
import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.api.Pagination;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class FeedbackController {
    private static final String FIELDS = "id,type,content,contact,status,reply,replied_at,created_at,updated_at";
    private final JdbcTemplate jdbc;
    private final ApiSupport api;
    public FeedbackController(JdbcTemplate jdbc, ApiSupport api) { this.jdbc = jdbc; this.api = api; }

    @GetMapping("/feedback")
    public ApiResponse<Map<String,Object>> index(@RequestParam(name="page", defaultValue="1") int page,
                                                 @RequestParam(name="page_size", defaultValue="20") int pageSize,
                                                 HttpServletRequest request) {
        page = Math.max(1, page); pageSize = Math.max(1, Math.min(30, pageSize));
        long userId = api.user(request).id();
        Long total = jdbc.queryForObject("select count(*) from ci_feedback where user_id=?", Long.class, userId);
        List<Map<String,Object>> rows = jdbc.queryForList("select "+FIELDS+" from ci_feedback where user_id=? order by id desc limit ? offset ?", userId, pageSize, (page-1)*pageSize);
        return ApiResponse.ok(Pagination.of(rows, total == null ? 0 : total, page, pageSize));
    }

    @GetMapping("/feedback-detail/{id}")
    public ApiResponse<Map<String,Object>> detail(@PathVariable long id, HttpServletRequest request) {
        List<Map<String,Object>> rows = jdbc.queryForList("select "+FIELDS+" from ci_feedback where id=? and user_id=? limit 1", id, api.user(request).id());
        if (rows.isEmpty()) throw new BusinessException(404, org.springframework.http.HttpStatus.NOT_FOUND, "反馈不存在");
        return ApiResponse.ok(rows.getFirst());
    }

    @PostMapping("/feedback")
    public ApiResponse<Map<String,Object>> create(@RequestBody FeedbackRequest body, HttpServletRequest request) {
        String content = body.content() == null ? "" : body.content().trim();
        int count = content.codePointCount(0, content.length());
        if (count < 5 || count > 1000) throw new BusinessException("反馈内容需为5-1000字");
        String type = truncate(body.type() == null ? "suggestion" : body.type(), 30);
        String contact = truncate(body.contact() == null ? "" : body.contact().trim(), 100);
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("insert into ci_feedback(user_id,type,content,contact,status,reply,created_at,updated_at) values(?,?,?,?,\'pending\',\'\',?,?)",
                api.user(request).id(), type, content, contact, now, now);
        Long id = jdbc.queryForObject("select last_insert_id()", Long.class);
        return ApiResponse.ok(Map.of("id", id), "反馈已提交，我们会尽快处理");
    }

    private String truncate(String value, int max) {
        return value.codePointCount(0, value.length()) <= max ? value : value.substring(0, value.offsetByCodePoints(0, max));
    }
    public record FeedbackRequest(String type, String content, String contact) {}
}

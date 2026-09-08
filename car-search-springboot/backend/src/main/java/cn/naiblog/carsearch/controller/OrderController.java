package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.ApiSupport;
import cn.naiblog.carsearch.api.Pagination;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final JdbcTemplate jdbc;
    private final ApiSupport api;
    public OrderController(JdbcTemplate jdbc, ApiSupport api) { this.jdbc = jdbc; this.api = api; }

    @GetMapping
    public ApiResponse<Map<String,Object>> index(@RequestParam(name="page", defaultValue="1") int page,
                                                 @RequestParam(name="page_size", defaultValue="10") int pageSize,
                                                 HttpServletRequest request) {
        page = Math.max(1, page); pageSize = Math.max(1, Math.min(30, pageSize));
        long userId = api.user(request).id();
        Long total = jdbc.queryForObject("select count(*) from ci_order where user_id=? and paid_at is not null", Long.class, userId);
        List<Map<String,Object>> rows = jdbc.queryForList("""
                select o.order_no,o.service_name,o.status,o.paid_at,o.queried_at,o.created_at,
                       p.wechat_order_type,p.refund_status
                from ci_order o left join ci_payment p on p.order_id=o.id
                where o.user_id=? and o.paid_at is not null order by o.id desc limit ? offset ?
                """, userId, pageSize, (page - 1) * pageSize);
        List<Map<String,Object>> data = rows.stream().map(this::safeOrder).toList();
        return ApiResponse.ok(Pagination.of(data, total == null ? 0 : total, page, pageSize));
    }

    private Map<String,Object> safeOrder(Map<String,Object> row) {
        Map<String,Object> safe = new LinkedHashMap<>();
        for (String key : List.of("order_no","service_name","status","paid_at","queried_at","created_at")) safe.put(key, row.get(key));
        Object orderType = row.get("wechat_order_type");
        safe.put("payment_platform", orderType instanceof Number number && number.intValue() == 7 ? "ios" : "wechat");
        safe.put("refund_status", row.get("refund_status") == null ? "none" : row.get("refund_status"));
        return safe;
    }
}

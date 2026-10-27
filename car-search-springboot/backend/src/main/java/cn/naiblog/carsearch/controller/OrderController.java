package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.ApiSupport;
import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.api.Pagination;
import cn.naiblog.carsearch.config.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    /** 订单状态分组：与「我的」页四项统计和订单页分类保持一致 */
    static final Map<String, List<String>> STATUS_GROUPS = Map.of(
            "unpaid", List.of("pending_payment"),
            "querying", List.of("paid", "querying", "payment_review"),
            "success", List.of("success"),
            "closed", List.of("query_failed", "refunding", "refunded", "cancelled"));

    private final JdbcTemplate jdbc;
    private final ApiSupport api;
    private final SettingService settings;
    public OrderController(JdbcTemplate jdbc, ApiSupport api, SettingService settings) { this.jdbc = jdbc; this.api = api; this.settings = settings; }

    @GetMapping
    public ApiResponse<Map<String,Object>> index(@RequestParam(name="page", defaultValue="1") int page,
                                                 @RequestParam(name="page_size", defaultValue="10") int pageSize,
                                                 @RequestParam(name="group", required=false) String group,
                                                 HttpServletRequest request) {
        page = Math.max(1, page); pageSize = Math.max(1, Math.min(30, pageSize));
        long userId = api.user(request).id();
        List<Object> args = new ArrayList<>(List.of(userId));
        String where = "o.user_id=?";
        if (group != null && !group.isBlank() && !"all".equals(group)) {
            List<String> statuses = STATUS_GROUPS.get(group);
            if (statuses == null) throw new BusinessException("无效的订单分类");
            where += " and o.status in (" + String.join(",", Collections.nCopies(statuses.size(), "?")) + ")";
            args.addAll(statuses);
        }
        // count 查询必须与列表查询一致使用别名 o，否则 where 中的 o.xxx 无法解析
        Long total = jdbc.queryForObject("select count(*) from ci_order o where " + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        List<Map<String,Object>> rows = jdbc.queryForList("""
                select o.order_no,o.service_name,o.status,o.amount,o.client_platform,o.paid_at,o.queried_at,o.created_at,
                       s.code service_code,s.icon service_icon,
                       p.wechat_order_type,p.refund_status,p.refund_source
                from ci_order o
                left join ci_service s on s.id=o.service_id
                left join ci_payment p on p.order_id=o.id
                where %s order by o.id desc limit ? offset ?
                """.formatted(where), pageArgs.toArray());
        List<Map<String,Object>> data = rows.stream().map(this::safeOrder).toList();
        return ApiResponse.ok(Pagination.of(data, total == null ? 0 : total, page, pageSize));
    }

    /**
     * 订单统计：待付款 / 查询中 / 查询成功 / 已关闭 / 全部。
     * 分组口径见 STATUS_GROUPS；total 为该用户全部订单（含已取消）。
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String,Object>> stats(HttpServletRequest request) {
        long userId = api.user(request).id();
        List<Map<String,Object>> rows = jdbc.queryForList(
                "select status,count(*) cnt from ci_order where user_id=? group by status", userId);
        Map<String,Integer> byStatus = new LinkedHashMap<>();
        for (Map<String,Object> row : rows) {
            Number cnt = (Number) row.get("cnt");
            byStatus.merge(String.valueOf(row.get("status")), cnt == null ? 0 : cnt.intValue(), Integer::sum);
        }
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("unpaid", groupTotal(byStatus, "unpaid"));
        data.put("querying", groupTotal(byStatus, "querying"));
        data.put("success", groupTotal(byStatus, "success"));
        data.put("closed", groupTotal(byStatus, "closed"));
        data.put("total", byStatus.values().stream().mapToInt(Integer::intValue).sum());
        return ApiResponse.ok(data);
    }

    private int groupTotal(Map<String,Integer> byStatus, String group) {
        int sum = 0;
        for (String status : STATUS_GROUPS.get(group)) sum += byStatus.getOrDefault(status, 0);
        return sum;
    }

    private Map<String,Object> safeOrder(Map<String,Object> row) {
        Map<String,Object> safe = new LinkedHashMap<>();
        for (String key : List.of("order_no","service_name","status","amount","paid_at","queried_at","created_at","service_code","service_icon")) safe.put(key, row.get(key));
        Object orderType = row.get("wechat_order_type");
        String platform = String.valueOf(row.getOrDefault("client_platform", ""));
        if (!List.of("android","ios").contains(platform)) platform = orderType instanceof Number number && number.intValue() == 7 ? "ios" : "android";
        safe.put("payment_platform", platform);
        safe.put("refund_status", row.get("refund_status") == null ? "none" : row.get("refund_status"));
        safe.put("refund_source", row.get("refund_source"));
        safe.put("auto_refund_enabled", "android".equals(platform) && "1".equals(settings.value("android_auto_refund_query_failed", "0")));
        return safe;
    }
}

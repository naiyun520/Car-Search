package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.ApiSupport;
import cn.naiblog.carsearch.api.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 查询进度只读取本人订单与持久任务，不接收供应商推送报告。 */
@RestController
@RequestMapping
public class QueryStatusController {
    private final JdbcTemplate jdbc;
    private final ApiSupport api;

    public QueryStatusController(JdbcTemplate jdbc, ApiSupport api) {
        this.jdbc = jdbc;
        this.api = api;
    }

    @GetMapping("/api/v1/query-status/{queryId}")
    public ApiResponse<Map<String, Object>> queryStatus(@PathVariable String queryId, HttpServletRequest request) {
        long userId = api.user(request).id();
        List<Map<String,Object>> rows=jdbc.queryForList("""
                select o.status,o.provider_code,o.queried_at,o.created_at,j.state job_state
                from ci_order o left join ci_query_job j on j.order_id=o.id
                where o.provider_request_id=? and o.user_id=? order by o.id desc limit 1
                """,queryId,userId);
        if(rows.isEmpty())throw new BusinessException("查询记录不存在或无权访问");
        Map<String, Object> row = rows.getFirst();
        String orderStatus = String.valueOf(row.get("status"));
        String status = "success".equals(orderStatus) ? "completed" : Set.of("query_failed","refunding","refunded","cancelled").contains(orderStatus) ? "failed" : "processing";
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 0);
        response.put("query_id", queryId);
        response.put("status", status);
        response.put("created_at", row.get("created_at"));
        if ("failed".equals(status)) {
            response.put("result_code", row.get("provider_code"));
            response.put("result_message", "查询未完成，请在订单详情查看处理结果");
            response.put("completed_at", row.get("queried_at"));
        }
        return ApiResponse.ok(response);
    }
}

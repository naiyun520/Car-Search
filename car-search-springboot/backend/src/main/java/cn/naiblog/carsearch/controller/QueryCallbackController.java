package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.service.PaymentDeliveryService;
import cn.naiblog.carsearch.service.ProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 查询回调控制器
 * 1. 接收供应商回调结果 POST /api/callback/provider/{serviceId}
 * 2. 小程序轮询查询状态 GET /api/query-status/{queryId}
 */
@RestController
public class QueryCallbackController {
    private static final Logger log = LoggerFactory.getLogger(QueryCallbackController.class);
    private final JdbcTemplate jdbc;
    private final ProviderService providerService;
    private final CryptoService crypto;
    private final PaymentDeliveryService delivery;
    private final ObjectMapper mapper;

    public QueryCallbackController(JdbcTemplate jdbc, ProviderService providerService,
                                   CryptoService crypto, PaymentDeliveryService delivery, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.providerService = providerService;
        this.crypto = crypto;
        this.delivery = delivery;
        this.mapper = mapper;
    }

    /**
     * 供应商回调接收端点
     * 供应商处理完成后会 POST 结果到此地址
     * URL 格式: /api/callback/provider/{serviceId}?query_id=xxx
     */
    @PostMapping("/api/callback/provider/{serviceId}")
    @Transactional
    public Map<String, Object> receiveCallback(
            @PathVariable int serviceId,
            @RequestParam(value = "query_id", required = false) String queryIdParam,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "HTTP-X-CA-PROXY-SIGNATURE", required = false) String signature,
            @RequestHeader(value = "Content-Type", required = false) String contentType) {

        log.info("[Callback] 收到供应商回调 serviceId={}, queryIdParam={}, signature={}, body={}",
                serviceId, queryIdParam, signature, body);

        // 1. 验证 serviceId 存在
        List<Map<String, Object>> services = jdbc.queryForList(
                "select id, callback_secret, callback_mode from ci_service where id=?", serviceId);
        if (services.isEmpty()) {
            log.warn("[Callback] serviceId={} 不存在", serviceId);
            return Map.of("code", -1, "message", "服务不存在");
        }
        Map<String, Object> service = services.getFirst();

        // 2. 验证回调模式
        String callbackMode = String.valueOf(service.getOrDefault("callback_mode", "none"));
        if (!"callback".equals(callbackMode)) {
            log.warn("[Callback] serviceId={} 回调模式未启用, mode={}", serviceId, callbackMode);
            return Map.of("code", -1, "message", "回调模式未启用");
        }

        // 3. 验证签名（可选，取决于供应商是否发送）
        String expectedSecret = String.valueOf(service.getOrDefault("callback_secret", "")).trim();
        if (!expectedSecret.isBlank() && signature != null) {
            // 简单签名验证：比较令牌值
            if (!expectedSecret.equals(signature)) {
                log.warn("[Callback] 签名验证失败 serviceId={}, expected={}, actual={}",
                        serviceId, expectedSecret.substring(0, Math.min(4, expectedSecret.length())) + "****",
                        signature.substring(0, Math.min(4, signature.length())) + "****");
                return Map.of("code", -1, "message", "签名验证失败");
            }
            log.info("[Callback] 签名验证通过 serviceId={}", serviceId);
        }

        // 4. 从 body 或 URL 参数中提取 query_id
        String queryId = queryIdParam;
        if ((queryId == null || queryId.isBlank()) && body != null) {
            // 尝试从 body 中提取 query_id / code_id / order_id
            for (String key : new String[]{"query_id", "code_id", "order_id"}) {
                Object val = body.get(key);
                if (val != null) {
                    queryId = String.valueOf(val).trim();
                    if (!queryId.isBlank()) break;
                }
            }
            // 尝试从 result 子对象中提取
            if ((queryId == null || queryId.isBlank()) && body.get("result") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) body.get("result");
                for (String key : new String[]{"query_id", "code_id", "order_id"}) {
                    Object val = result.get(key);
                    if (val != null) {
                        queryId = String.valueOf(val).trim();
                        if (!queryId.isBlank()) break;
                    }
                }
            }
        }
        log.info("[Callback] 提取 queryId={}", queryId);

        // 5. 更新查询记录
        if (queryId != null && !queryId.isBlank()) {
            String resultJson = body != null ? toJson(body) : "{}";
            String resultCode = "";
            String resultMessage = "";
            String status = "completed";

            // 提取结果状态
            if (body != null) {
                resultCode = String.valueOf(body.getOrDefault("number", body.getOrDefault("code", "")));
                resultMessage = String.valueOf(body.getOrDefault("message", ""));

                // 检查是否有嵌套的 result 状态
                if (body.get("result") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) body.get("result");
                    String queryStatus = String.valueOf(result.getOrDefault("query_status",
                            result.getOrDefault("result_status", "")));
                    // query_status: 3=查询成功, 7=查询失败
                    if ("7".equals(queryStatus)) {
                        status = "failed";
                    }
                    // 也检查内层 result.status: 1=查询中, 2=查询成功, 3=查询失败
                    if (result.get("result") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> inner = (Map<String, Object>) result.get("result");
                        Object innerStatus = inner.get("status");
                        if (innerStatus instanceof Number n && n.intValue() == 3) {
                            status = "failed";
                        }
                    }
                }

                // number != 200 表示失败
                if (!"200".equals(resultCode)) {
                    status = "failed";
                }
            }

            int updated = jdbc.update(
                    "update ci_query set status=?, result_json=?, result_code=?, result_message=?, " +
                            "callback_received_at=?, completed_at=?, updated_at=? where query_id=? and status in ('pending','processing')",
                    status, resultJson, resultCode, resultMessage,
                    LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), queryId);

            if (updated > 0) {
                log.info("[Callback] 查询结果已更新 queryId={}, status={}, resultCode={}", queryId, status, resultCode);
            } else {
                log.warn("[Callback] 未找到待处理的查询记录 queryId={}，可能是重复回调", queryId);
            }

            // 6. 更新订单状态（通过 provider_request_id 关联）
            if ("completed".equals(status) && body != null) {
                updateOrderFromCallback(serviceId, queryId, body, resultCode);
            }
        } else {
            log.warn("[Callback] 无法提取 queryId，body={}", body);
        }

        // 6. 返回成功（供应商要求收到 "success"）
        return Map.of("code", 0, "message", "success");
    }

    /**
     * 小程序轮询查询状态
     * GET /api/query-status/{queryId}
     */
    @GetMapping("/api/query-status/{queryId}")
    public Map<String, Object> queryStatus(@PathVariable String queryId) {
        log.info("[QueryStatus] 查询状态 queryId={}", queryId);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, query_id, service_id, status, result_json, result_code, result_message, " +
                        "callback_received_at, fallback_at, created_at from ci_query where query_id=?", queryId);

        if (rows.isEmpty()) {
            return Map.of("code", -1, "message", "查询记录不存在");
        }

        Map<String, Object> row = rows.getFirst();
        String status = String.valueOf(row.getOrDefault("status", "unknown"));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 0);
        response.put("query_id", queryId);
        response.put("status", status);
        response.put("created_at", row.get("created_at"));

        if ("completed".equals(status) || "failed".equals(status)) {
            String resultJson = String.valueOf(row.getOrDefault("result_json", "{}"));
            response.put("result_json", resultJson);
            response.put("result_code", row.get("result_code"));
            response.put("result_message", row.get("result_message"));
            response.put("completed_at", row.get("callback_received_at"));
        }

        // 如果是 pending/processing 且超过超时时间，触发降级轮询
        if ("pending".equals(status) || "processing".equals(status)) {
            int serviceId = ((Number) row.get("service_id")).intValue();
            List<Map<String, Object>> services = jdbc.queryForList(
                    "select callback_timeout_sec from ci_service where id=?", serviceId);

            int timeoutSec = services.isEmpty() ? 120 : ((Number) services.getFirst()
                    .getOrDefault("callback_timeout_sec", 120)).intValue();

            LocalDateTime createdAt = (LocalDateTime) row.get("created_at");
            if (createdAt != null && createdAt.plusSeconds(timeoutSec).isBefore(LocalDateTime.now())) {
                // 超时，触发降级轮询
                log.info("[QueryStatus] 查询超时，触发降级轮询 queryId={}, serviceId={}", queryId, serviceId);
                triggerFallbackPoll(row, serviceId);
                response.put("status", "processing");
                response.put("fallback", true);
            }
        }

        return response;
    }

    /**
     * 触发降级轮询：调用供应商获取报告接口
     */
    private void triggerFallbackPoll(Map<String, Object> queryRow, int serviceId) {
        try {
            String queryId = String.valueOf(queryRow.get("query_id"));
            // 标记降级时间
            jdbc.update("update ci_query set fallback_at=?, status='processing', updated_at=? where id=?",
                    LocalDateTime.now(), LocalDateTime.now(), queryRow.get("id"));

            // 异步执行降级轮询（在新线程中执行，避免阻塞请求）
            CompletableFuture.runAsync(() -> {
                try {
                    providerService.pollForResult(serviceId, queryId);
                    // 降级轮询成功，更新订单状态
                    updateOrderFromFallback(serviceId, queryId);
                } catch (Exception e) {
                    log.error("[Fallback] 降级轮询失败 queryId={}, error={}", queryId, e.getMessage(), e);
                    jdbc.update("update ci_query set status='failed', result_message=?, completed_at=?, updated_at=? where query_id=?",
                            "降级轮询失败: " + e.getMessage(), LocalDateTime.now(), LocalDateTime.now(), queryId);
                    // 降级轮询也失败，更新订单为 query_failed
                    jdbc.update("update ci_order set status='query_failed', updated_at=? where provider_request_id=? and status='querying'",
                            LocalDateTime.now(), queryId);
                }
            });
        } catch (Exception e) {
            log.error("[Fallback] 触发降级轮询异常", e);
        }
    }

    /**
     * 回调成功后，解析数据并更新订单
     */
    private void updateOrderFromCallback(int serviceId, String queryId, Map<String, Object> body, String resultCode) {
        try {
            // 通过 provider_request_id=queryId 找到订单
            List<Map<String, Object>> orders = jdbc.queryForList(
                    "select id, order_no, service_snapshot_cipher from ci_order where provider_request_id=? and status='querying'", queryId);
            if (orders.isEmpty()) {
                log.warn("[Callback] 未找到关联订单 queryId={}", queryId);
                return;
            }
            Map<String, Object> order = orders.getFirst();
            int orderId = ((Number) order.get("id")).intValue();

            // 获取服务配置（用于 normalize）
            Map<String, Object> service = jdbc.queryForMap("select * from ci_service where id=?", serviceId);

            // 从回调 body 中提取 result 数据
            Object resultObj = body.get("result");
            Map<String, Object> resultData;
            if (resultObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> r = (Map<String, Object>) resultObj;
                resultData = r;
            } else {
                resultData = body;
            }

            // normalize 数据
            List<Map<String, Object>> normalized = providerService.normalize(service, resultData);
            LocalDateTime now = LocalDateTime.now();

            jdbc.update(
                    "update ci_order set status='success', provider_code=?, result_cipher=?, queried_at=?, updated_at=? where id=?",
                    resultCode, crypto.encrypt(Map.of("items", normalized)), now, now, orderId);

            log.info("[Callback] 订单已更新为成功 orderId={}, queryId={}", orderId, queryId);

            // 触发发货和邮件通知
            try { delivery.deliver(String.valueOf(order.get("order_no")), false); } catch (Exception ignored) {}
        } catch (Exception e) {
            log.error("[Callback] 更新订单失败 queryId={}", queryId, e);
        }
    }

    /**
     * 降级轮询成功后，从 ci_query.result_json 解析数据并更新订单
     */
    private void updateOrderFromFallback(int serviceId, String queryId) {
        try {
            // 从 ci_query 读取降级轮询的结果
            List<Map<String, Object>> queries = jdbc.queryForList(
                    "select result_json from ci_query where query_id=? and status='completed'", queryId);
            if (queries.isEmpty()) {
                log.warn("[Fallback] 未找到已完成的查询记录 queryId={}", queryId);
                return;
            }
            String resultJson = String.valueOf(queries.getFirst().getOrDefault("result_json", "{}"));
            Map<String, Object> body = mapper.readValue(resultJson, new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});

            // 复用回调更新逻辑
            updateOrderFromCallback(serviceId, queryId, body, "200");
        } catch (Exception e) {
            log.error("[Fallback] 更新订单失败 queryId={}", queryId, e);
        }
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.ApiSupport;
import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.security.CurrentUser;
import cn.naiblog.carsearch.service.EmailNotificationService;
import cn.naiblog.carsearch.service.PaymentDeliveryService;
import cn.naiblog.carsearch.service.ProviderService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class QueryCallbackControllerTest {
    private JdbcTemplate jdbc;
    private ApiSupport api;
    private ProviderService providerService;
    private CryptoService crypto;
    private EmailNotificationService emailService;
    private QueryCallbackController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        api = Mockito.mock(ApiSupport.class);
        providerService = Mockito.mock(ProviderService.class);
        crypto = Mockito.mock(CryptoService.class);
        emailService = Mockito.mock(EmailNotificationService.class);
        controller = new QueryCallbackController(jdbc, providerService, crypto,
                Mockito.mock(PaymentDeliveryService.class), emailService, new ObjectMapper(), api);
        request = new MockHttpServletRequest();
        when(api.user(Mockito.any())).thenReturn(new CurrentUser(7L, "openid", null, null, null, null));
    }

    /** 回归：轮询端点必须挂在 /api/v1 下（小程序 API_BASE_URL 前缀），否则全量 404 */
    @Test
    void queryStatusMappedUnderApiV1Prefix() throws Exception {
        GetMapping mapping = QueryCallbackController.class
                .getMethod("queryStatus", String.class, HttpServletRequest.class)
                .getAnnotation(GetMapping.class);

        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/api/v1/query-status/{queryId}"}, mapping.value());
    }

    /** 归属校验：非本人订单的 queryId 一律拒绝 */
    @Test
    void queryStatusRejectsQueryIdNotOwnedByUser() {
        when(jdbc.queryForList(contains("provider_request_id"), eq("CIDFOREIGN"), eq(7L)))
                .thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.queryStatus("CIDFOREIGN", request));
        assertEquals("查询记录不存在或无权访问", error.getMessage());
    }

    /** 正常路径：归属通过后返回 ApiResponse 信封，data 内保留 code=0 兼容旧版小程序 */
    @Test
    void queryStatusReturnsStatusEnvelopeForOwnedOrder() {
        Map<String, Object> owned = Map.of("id", 1);
        when(jdbc.queryForList(contains("provider_request_id"), eq("CID2001"), eq(7L)))
                .thenReturn(List.of(owned));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("query_id", "CID2001");
        row.put("service_id", 5);
        row.put("status", "completed");
        row.put("result_json", "{}");
        row.put("result_code", "200");
        row.put("result_message", "");
        row.put("created_at", LocalDateTime.of(2026, 10, 27, 18, 0));
        when(jdbc.queryForList(contains("from ci_query"), eq("CID2001")))
                .thenReturn(List.of(row));

        ApiResponse<Map<String, Object>> response = controller.queryStatus("CID2001", request);

        assertEquals(0, response.code());
        assertNotNull(response.data());
        assertEquals("completed", response.data().get("status"));
        assertEquals(0, response.data().get("code"));
    }

    /** 回调报文：mock 掉 jdbc.update，收集实际执行的 SQL，便于断言语义 */
    private List<String> stubCallbackDependencies(int serviceId) {
        List<String> sqls = new ArrayList<>();
        when(jdbc.queryForList(contains("from ci_service"), eq(serviceId)))
                .thenReturn(List.of(Map.of("id", serviceId, "callback_secret", "cb-secret", "callback_mode", "callback")));
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(inv -> {
            sqls.add(inv.getArgument(0));
            return 1;
        });
        return sqls;
    }

    /** 线上真实报文（2026-10-30 详细车险报告）：result_status=3 必须判失败，不得交付空报告 */
    @Test
    void callbackResultStatus3MarksOrderFailedInsteadOfSuccess() {
        List<String> sqls = stubCallbackDependencies(32);
        when(jdbc.queryForList(contains("status='query_failed'"), eq("CIR20261030104111209002668730364")))
                .thenReturn(List.of(Map.of("id", 11)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query_id", "CIR20261030104111209002668730364");
        result.put("vin", "LVG8M51K2FG508778");
        result.put("car_no", "新MK7N66");
        result.put("result_status", 3);
        result.put("content", null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", "2.0.0");
        body.put("object", "CarInsurance/getReport");
        body.put("message", "");
        body.put("number", 200);
        body.put("result", result);

        Map<String, Object> response = controller.receiveCallback(32, null, body, "cb-secret", "application/json");

        assertEquals(0, response.get("code"));
        assertTrue(sqls.stream().anyMatch(sql -> sql.startsWith("update ci_query set status=?")), "应落库 ci_query 状态");
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("status='query_failed'")), "订单应更新为 query_failed");
        assertFalse(sqls.stream().anyMatch(sql -> sql.contains("status='success'")), "查询失败不得写入成功结果");
        // 失败必须发邮件通知（与同步链路 email.queryFailed 对齐）
        Mockito.verify(emailService).queryFailed(eq(11), eq("query_failed"), eq("CIR20261030104111209002668730364"), anyString());
    }

    /** result_status=2 表示供应商仍在查询中：仅回 200 应答，不落任何状态 */
    @Test
    void callbackResultStatus2KeepsWaitingWithoutUpdating() {
        List<String> sqls = stubCallbackDependencies(32);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query_id", "CIR-PENDING-1");
        result.put("result_status", 2);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("number", 200);
        body.put("result", result);

        Map<String, Object> response = controller.receiveCallback(32, null, body, "cb-secret", "application/json");

        assertEquals(0, response.get("code"));
        assertTrue(sqls.isEmpty(), "查询中不应写库，实际=" + sqls);
    }

    /** result_status=1：拍平后 normalize 并交付，保证失败修复没有破坏成功通路 */
    @Test
    void callbackResultStatus1DeliversFlattenedReport() throws Exception {
        List<String> sqls = stubCallbackDependencies(32);
        when(jdbc.queryForList(contains("from ci_order"), eq("CIR-OK-1")))
                .thenReturn(List.of(Map.of("id", 11, "order_no", "NO-11", "service_snapshot_cipher", "SNAP")));
        when(jdbc.queryForMap(contains("from ci_service where id=?"), eq(32)))
                .thenReturn(Map.of("id", 32, "result_schema", "{}"));
        when(crypto.encrypt(any())).thenReturn("CIPHER");
        when(providerService.normalize(any(), any())).thenReturn(List.of());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query_id", "CIR-OK-1");
        result.put("vin", "LVG8M51K2FG508778");
        result.put("result_status", 1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("number", 200);
        body.put("result", result);

        Map<String, Object> response = controller.receiveCallback(32, null, body, "cb-secret", "application/json");

        assertEquals(0, response.get("code"));
        assertTrue(sqls.stream().anyMatch(sql -> sql.contains("status='success'")), "成功回调应交付结果");
        assertTrue(sqls.stream().noneMatch(sql -> sql.contains("query_failed")));
        Mockito.verify(emailService, Mockito.never()).queryFailed(any(), any(), any(), any());
    }

    /** 回调-only：即使查询超时也绝不允许触发任何供应商轮询（11-01 0点 579 次计费调用事故回归） */
    @Test
    void queryStatusNeverTriggersProviderPolling() {
        when(jdbc.queryForList(contains("provider_request_id"), eq("CIDFB1"), eq(7L)))
                .thenReturn(List.of(Map.of("id", 3)));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("query_id", "CIDFB1");
        row.put("service_id", 5);
        row.put("status", "processing");
        row.put("result_json", "{}");
        row.put("fallback_at", null);
        row.put("created_at", LocalDateTime.now().minusMinutes(10));
        when(jdbc.queryForList(contains("from ci_query"), eq("CIDFB1"))).thenReturn(List.of(row));

        ApiResponse<Map<String, Object>> response = controller.queryStatus("CIDFB1", request);

        assertFalse(Boolean.TRUE.equals(response.data().get("fallback")), "轮询取报告已下线，不得再出现 fallback 标记");
        Mockito.verifyNoInteractions(providerService);
        Mockito.verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
    }

    /** 已上线小程序只认 completed/failed：调度器判的 timeout 必须对外映射为 failed，否则旧前端永远轮询 */
    @Test
    void queryStatusMapsTimeoutToFailedForLegacyClients() {
        when(jdbc.queryForList(contains("provider_request_id"), eq("CIDTMO"), eq(7L)))
                .thenReturn(List.of(Map.of("id", 4)));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 5);
        row.put("query_id", "CIDTMO");
        row.put("service_id", 5);
        row.put("status", "timeout");
        row.put("result_json", "{}");
        row.put("result_code", "timeout");
        row.put("result_message", "回调超时：超过 120 秒未收到供应商查询结果");
        row.put("callback_received_at", null);
        row.put("fallback_at", null);
        row.put("created_at", LocalDateTime.now().minusMinutes(10));
        when(jdbc.queryForList(contains("from ci_query"), eq("CIDTMO"))).thenReturn(List.of(row));

        ApiResponse<Map<String, Object>> response = controller.queryStatus("CIDTMO", request);

        assertEquals("failed", response.data().get("status"), "timeout 必须映射为 failed 供旧版前端识别终态");
        assertEquals("回调超时：超过 120 秒未收到供应商查询结果", response.data().get("result_message"));
    }

    /** 订单存在但 ci_query 缺失 → 业务错误而非 500 */
    @Test
    void queryStatusThrowsWhenQueryRecordMissing() {        when(jdbc.queryForList(contains("provider_request_id"), eq("CIDMISS"), eq(7L)))
                .thenReturn(List.of(Map.of("id", 9)));
        when(jdbc.queryForList(contains("from ci_query"), eq("CIDMISS")))
                .thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.queryStatus("CIDMISS", request));
        assertEquals("查询记录不存在", error.getMessage());
    }
}

package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.ApiSupport;
import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.security.CurrentUser;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class QueryCallbackControllerTest {
    private JdbcTemplate jdbc;
    private ApiSupport api;
    private QueryCallbackController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        api = Mockito.mock(ApiSupport.class);
        controller = new QueryCallbackController(jdbc,
                Mockito.mock(ProviderService.class),
                Mockito.mock(CryptoService.class),
                Mockito.mock(PaymentDeliveryService.class),
                new ObjectMapper(), api);
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

    /** 订单存在但 ci_query 缺失 → 业务错误而非 500 */
    @Test
    void queryStatusThrowsWhenQueryRecordMissing() {
        when(jdbc.queryForList(contains("provider_request_id"), eq("CIDMISS"), eq(7L)))
                .thenReturn(List.of(Map.of("id", 9)));
        when(jdbc.queryForList(contains("from ci_query"), eq("CIDMISS")))
                .thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.queryStatus("CIDMISS", request));
        assertEquals("查询记录不存在", error.getMessage());
    }
}

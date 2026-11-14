package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.ApiSupport;
import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class QueryStatusControllerTest {
    private JdbcTemplate jdbc;
    private ApiSupport api;
    private QueryStatusController controller;

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        api = Mockito.mock(ApiSupport.class);
        controller = new QueryStatusController(jdbc, api);
        when(api.user(any())).thenReturn(new CurrentUser(7L, "openid", null, null, null, null));
    }

    @Test
    void queryStatusMappedUnderApiV1Prefix() throws Exception {
        GetMapping mapping = QueryStatusController.class.getMethod("queryStatus", String.class, jakarta.servlet.http.HttpServletRequest.class).getAnnotation(GetMapping.class);
        assertArrayEquals(new String[]{"/api/v1/query-status/{queryId}"}, mapping.value());
    }

    @Test
    void queryStatusRejectsQueryIdNotOwnedByUser() {
        when(jdbc.queryForList(contains("provider_request_id"), eq("FOREIGN"), eq(7L))).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> controller.queryStatus("FOREIGN", new MockHttpServletRequest()));
    }

    @Test
    void queryStatusReturnsLegacyCompatibleEnvelope() {

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", "success"); row.put("result_json", "PRIVATE-RAW"); row.put("result_code", "200");
        row.put("result_message", ""); row.put("created_at", LocalDateTime.now()); row.put("callback_received_at", LocalDateTime.now());
        when(jdbc.queryForList(contains("from ci_order"), eq("Q-2"),eq(7L))).thenReturn(List.of(row));
        ApiResponse<Map<String, Object>> response = controller.queryStatus("Q-2", new MockHttpServletRequest());
        assertEquals(0, response.code());
        assertEquals(0, response.data().get("code"));
        assertEquals("completed", response.data().get("status"));
        assertFalse(response.data().containsKey("result_json"));
    }
}

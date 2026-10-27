package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiSupport;
import cn.naiblog.carsearch.config.SettingService;
import cn.naiblog.carsearch.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderControllerStatsTest {
    private JdbcTemplate jdbc;
    private ApiSupport api;
    private OrderController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        api = Mockito.mock(ApiSupport.class);
        controller = new OrderController(jdbc, api, Mockito.mock(SettingService.class));
        request = new MockHttpServletRequest();
        when(api.user(any())).thenReturn(new CurrentUser(7L, "openid", null, null, null, null));
    }

    /** 状态分组统计：待付款/查询中/查询成功/已关闭/全部 的聚合口径 */
    @Test
    void statsAggregatesStatusGroups() {
        when(jdbc.queryForList(contains("group by status"), eq(7L))).thenReturn(List.of(
                Map.of("status", "success", "cnt", 3),
                Map.of("status", "querying", "cnt", 1),
                Map.of("status", "paid", "cnt", 2),
                Map.of("status", "pending_payment", "cnt", 2),
                Map.of("status", "cancelled", "cnt", 1),
                Map.of("status", "query_failed", "cnt", 1)));

        Map<String, Object> data = controller.stats(request).data();

        assertEquals(2, data.get("unpaid"));        // pending_payment×2
        assertEquals(3, data.get("querying"));      // querying×1 + paid×2
        assertEquals(3, data.get("success"));       // success×3
        assertEquals(2, data.get("closed"));        // cancelled×1 + query_failed×1
        assertEquals(10, data.get("total"));        // 全部状态求和
    }

    /** 列表按分类过滤：unpaid → o.status in (pending_payment)，计数与查询条件一致 */
    @Test
    void indexFiltersByStatusGroup() {
        when(jdbc.queryForObject(contains("count(*)"), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForList(any(String.class), any(Object[].class))).thenReturn(List.of());

        controller.index(1, 10, "unpaid", request);

        // count 查询必须带别名 o（from ci_order o），否则 o.user_id 无法解析
        verify(jdbc).queryForObject(contains("from ci_order o where o.user_id=?"), eq(Long.class), eq(7L), eq("pending_payment"));
    }

    /** 列表返回订单金额（元制小数），供订单卡片右下角价格展示 */
    @Test
    void indexReturnsOrderAmount() {
        when(jdbc.queryForObject(contains("count(*)"), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbc.queryForList(any(String.class), any(Object[].class))).thenReturn(List.of(Map.ofEntries(
                Map.entry("order_no", "NO1"), Map.entry("service_name", "测试服务"),
                Map.entry("status", "success"), Map.entry("amount", new java.math.BigDecimal("19.90")),
                Map.entry("paid_at", "2026-10-28 10:00"), Map.entry("queried_at", "2026-10-28 10:01"),
                Map.entry("created_at", "2026-10-28 09:59"), Map.entry("service_code", "plate_basic"),
                Map.entry("service_icon", "builtin:plate"), Map.entry("client_platform", "android"),
                Map.entry("refund_status", "none"))));

        Map<String, Object> page = controller.index(1, 10, null, request).data();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) page.get("data");
        assertEquals(1, data.size());
        assertEquals(new java.math.BigDecimal("19.90"), data.get(0).get("amount"));
    }

    /** 未知分类直接报错，不发起查询 */
    @Test
    void indexRejectsUnknownGroup() {
        var error = assertThrows(cn.naiblog.carsearch.api.BusinessException.class,
                () -> controller.index(1, 10, "nonsense", request));
        assertEquals("无效的订单分类", error.getMessage());
    }
}

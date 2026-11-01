package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 回调-only 超时兜底：只落库+邮件，绝不允许调用供应商接口（2026-11-01 579 次计费调用事故回归） */
class MaintenanceSchedulerTest {
    private JdbcTemplate jdbc;
    private EmailNotificationService email;
    private MaintenanceScheduler scheduler;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        email = mock(EmailNotificationService.class);
        scheduler = new MaintenanceScheduler(jdbc, mock(PaymentLifecycleService.class),
                mock(AndroidAutoRefundService.class), mock(SettingService.class), email);
    }

    @Test
    void failStaleQueriesMarksOrderFailedAndNotifiesWithoutProviderCall() {
        when(jdbc.queryForList(contains("status='querying'")))
                .thenReturn(List.of(Map.of("order_id", 4022L, "provider_request_id", "CIR-TIMEOUT-1", "timeout_sec", 120)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        scheduler.failStaleQueries();

        verify(jdbc).update(contains("status='query_failed'"), eq("回调超时：超过 120 秒未收到供应商查询结果"), any(), any(), eq(4022L));
        verify(jdbc).update(contains("ci_query set status='timeout'"), any(), any(), any(), eq("CIR-TIMEOUT-1"));
        verify(email).queryFailed(eq(4022L), eq("timeout"), eq("CIR-TIMEOUT-1"), anyString());
    }

    /** 订单已被并发更新（update 返回 0）→ 不重复处理、不发邮件 */
    @Test
    void failStaleQueriesSkipsWhenOrderNoLongerQuerying() {
        when(jdbc.queryForList(contains("status='querying'")))
                .thenReturn(List.of(Map.of("order_id", 7L, "provider_request_id", "CIR-GONE", "timeout_sec", 120)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        assertDoesNotThrow(() -> scheduler.failStaleQueries());

        verify(jdbc, Mockito.never()).update(contains("ci_query set status='timeout'"), any(), any(), any(), any());
        verify(email, never()).queryFailed(any(), any(), any(), any());
    }

    /** 无超时订单 → 除查询外无任何写操作 */
    @Test
    void failStaleQueriesDoesNothingWhenNoneStale() {
        when(jdbc.queryForList(contains("status='querying'"))).thenReturn(List.of());

        scheduler.failStaleQueries();

        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
        verify(email, never()).queryFailed(any(), any(), any(), any());
    }

    /** callback_timeout_sec=0 表示永不超时：SQL 必须带 >0 过滤，回调多晚到都照常交付（2026-11-01 业务决策） */
    @Test
    void failStaleQueriesRespectsDisabledTimeout() {
        when(jdbc.queryForList(contains("status='querying'"))).thenReturn(List.of());

        scheduler.failStaleQueries();

        // 验证 SQL 含 >0 过滤：配置为 0 的服务永远不会被扫出
        verify(jdbc).queryForList(org.mockito.ArgumentMatchers.argThat(sql ->
                sql instanceof String s && s.contains("coalesce(s.callback_timeout_sec,120) > 0")));
        verify(jdbc, Mockito.never()).update(anyString(), any(Object[].class));
        verify(email, never()).queryFailed(any(), any(), any(), any());
    }
}

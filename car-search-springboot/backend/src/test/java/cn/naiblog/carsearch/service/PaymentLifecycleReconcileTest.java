package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.wechat.WechatPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 回调-only 成本模型回归：
 * 2026-11-01 01:03 事故——reconcilePending 曾把 querying 超 5 分钟的订单翻回 paid 并重新下单，
 * 与 failStaleQueries 互相踩踏（新单 8 秒即被判超时），每单白扣一次供应商下单费。
 */
class PaymentLifecycleReconcileTest {
    private JdbcTemplate jdbc;
    private QueryRunnerService queries;
    private PaymentLifecycleService lifecycle;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        queries = mock(QueryRunnerService.class);
        lifecycle = new PaymentLifecycleService(jdbc,
                mock(WechatPaymentService.class),
                mock(PaymentReconciliationService.class),
                queries,
                mock(PaymentDeliveryService.class),
                new ObjectMapper(),
                mock(PlatformTransactionManager.class),
                mock(EmailNotificationService.class));
    }

    /** 对账调度不得把 querying 订单翻回 paid 重新查询（每次 run 都是对供应商的计费下单） */
    @Test
    void reconcilePendingNeverResubmitsQueryingOrders() {
        when(jdbc.queryForList(contains("pending_payment"), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(List.of());
        when(jdbc.queryForList(contains("delivery_status"), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(List.of());
        when(jdbc.queryForList(contains("refund_status in"), org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenReturn(List.of());

        Map<String, Integer> result = lifecycle.reconcilePending(50);

        assertFalse(result.containsKey("recovered"), "自动恢复重跑已下线，不得再出现 recovered 指标");
        Mockito.verifyNoInteractions(queries);
        Mockito.verify(jdbc, Mockito.never())
                .update(contains("status='paid'"), Mockito.any(Object[].class));
        Mockito.verify(jdbc, Mockito.never())
                .queryForList(contains("status='querying'"), org.mockito.ArgumentMatchers.any(Object[].class));
    }
}

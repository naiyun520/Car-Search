package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminOrderBalanceTest {
    @Test void orderListShowsFirstBeforeAndLatestAfterBalance(){
        JdbcTemplate db=mock(JdbcTemplate.class);
        AdminOrderController controller=controller(db);
        Map<String,Object> order=new LinkedHashMap<>();
        order.put("id",9L);order.put("order_no","ORDER-9");order.put("user_id",2L);order.put("service_name","测试服务");
        order.put("amount",new BigDecimal("19.90"));order.put("status","query_failed");order.put("wechat_order_type",0);
        when(db.queryForObject(contains("count(*)"),eq(Long.class),any(Object[].class))).thenReturn(1L);
        when(db.queryForList(startsWith("select o.id"),any(Object[].class))).thenReturn(List.of(order));
        when(db.queryForList(contains("ci_provider_balance_snapshot"),any(Object[].class))).thenReturn(List.of(
                snapshot("before","100.0000","success",LocalDateTime.of(2026,11,15,10,0)),
                snapshot("after","98.5000","success",LocalDateTime.of(2026,11,15,10,1))));

        @SuppressWarnings("unchecked")
        List<Map<String,Object>> rows=(List<Map<String,Object>>)controller.orders(1,20,"","").data().get("data");
        assertEquals(new BigDecimal("100.0000"),rows.getFirst().get("provider_balance_before"));
        assertEquals(new BigDecimal("98.5000"),rows.getFirst().get("provider_balance_after"));
        assertEquals(new BigDecimal("-1.5000"),rows.getFirst().get("provider_balance_change"));
        assertEquals("captured",rows.getFirst().get("provider_balance_status"));
    }

    @Test void failedLatestAfterSnapshotDoesNotReuseAnOlderAfterBalance(){
        JdbcTemplate db=mock(JdbcTemplate.class);
        AdminOrderController controller=controller(db);
        Map<String,Object> order=new LinkedHashMap<>(Map.of("id",9L,"order_no","ORDER-9","user_id",2L,"service_name","测试服务","amount",new BigDecimal("19.90"),"status","query_failed","wechat_order_type",0));
        when(db.queryForObject(contains("count(*)"),eq(Long.class),any(Object[].class))).thenReturn(1L);
        when(db.queryForList(startsWith("select o.id"),any(Object[].class))).thenReturn(List.of(order));
        List<Map<String,Object>> snapshots=new ArrayList<>();
        snapshots.add(snapshot("before","100.0000","success",LocalDateTime.of(2026,11,15,10,0)));
        snapshots.add(snapshot("after","98.5000","success",LocalDateTime.of(2026,11,15,10,1)));
        Map<String,Object> failed=snapshot("after",null,"failed",LocalDateTime.of(2026,11,15,10,2));failed.put("error_message","余额接口超时");snapshots.add(failed);
        when(db.queryForList(contains("ci_provider_balance_snapshot"),any(Object[].class))).thenReturn(snapshots);

        @SuppressWarnings("unchecked")
        Map<String,Object> row=((List<Map<String,Object>>)controller.orders(1,20,"","").data().get("data")).getFirst();
        assertEquals("partial",row.get("provider_balance_status"));
        assertFalse(row.containsKey("provider_balance_after"));
        assertFalse(row.containsKey("provider_balance_change"));
        assertEquals("余额接口超时",row.get("provider_balance_error"));
    }

    private Map<String,Object> snapshot(String phase,String balance,String status,LocalDateTime at){
        Map<String,Object> row=new LinkedHashMap<>();row.put("linked_id",9L);row.put("phase",phase);row.put("status",status);row.put("observed_at",at);row.put("provider_name","测试供应商");if(balance!=null)row.put("balance",new BigDecimal(balance));return row;
    }
    private AdminOrderController controller(JdbcTemplate db){
        return new AdminOrderController(db,mock(CryptoService.class),mock(ObjectMapper.class),mock(PaymentReconciliationService.class),mock(PaymentLifecycleService.class),mock(QueryRunnerService.class),mock(QueryJobService.class),mock(AuditService.class),mock(EmailNotificationService.class));
    }
}

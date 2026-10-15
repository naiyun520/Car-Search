package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.wechat.WechatPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentReconciliationServiceTest {
    @Test
    void platformMismatchMovesOrderToManualReview() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);WechatPaymentService wechat=mock(WechatPaymentService.class);
        Map<String,Object> payment=new LinkedHashMap<>();payment.put("id",31L);payment.put("pay_env",0);payment.put("expected_order_type",0);
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(payment);
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        when(wechat.queryOrder("openid-1","ORDER12345678",0)).thenReturn(Map.of("status",2,"order_type",7,"paid_fee",990,"order_id","ORDER12345678","env_type",1));
        PaymentReconciliationService service=new PaymentReconciliationService(jdbc,wechat,new ObjectMapper(),mock(EmailNotificationService.class));
        Map<String,Object> order=new LinkedHashMap<>();order.put("id",9L);order.put("order_no","ORDER12345678");order.put("status","pending_payment");order.put("amount",new BigDecimal("9.90"));order.put("client_platform","android");order.put("expired_at",LocalDateTime.now().plusMinutes(20));
        assertEquals("payment_review",service.reconcile(order,Map.of("openid","openid-1")));
        verify(jdbc).update(startsWith("update ci_order set status='payment_review'"),any(Object[].class));
        verify(jdbc,never()).update(startsWith("update ci_order set status='paid'"),any(Object[].class));
    }
}

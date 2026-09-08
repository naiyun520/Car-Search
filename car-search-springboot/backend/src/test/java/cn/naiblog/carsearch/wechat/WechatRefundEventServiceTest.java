package cn.naiblog.carsearch.wechat;

import cn.naiblog.carsearch.config.SettingService;
import cn.naiblog.carsearch.security.CryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WechatRefundEventServiceTest {
    @Test void unmatchedIosInquiryIsApprovedAndArchived() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);CryptoService crypto=mock(CryptoService.class);SettingService settings=mock(SettingService.class);
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any())).thenReturn(List.of());
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
        when(crypto.encrypt(any())).thenReturn("encrypted");
        when(settings.value(anyString(),anyString())).thenAnswer(i->i.getArgument(1));
        WechatRefundEventService service=new WechatRefundEventService(jdbc,crypto,settings,new ObjectMapper());
        Map<String,Object> response=service.handle(Map.of("Event","xpay_subscribe_ios_refund_query_notify","pay_order_id","missing","refund_request_reason","ITEM_NOT_RECEIVED","provide_status",0));
        assertThat(response).containsEntry("result_code",0);
        assertThat(response.get("evidence")).asString().contains("未找到");
        verify(jdbc).update(startsWith("insert into ci_wechat_event"),any(Object[].class));
        verify(jdbc).update(startsWith("update ci_wechat_event set order_no"),any(Object[].class));
    }

    @Test void unknownEventIsAcknowledgedInsteadOfDiscarded() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);CryptoService crypto=mock(CryptoService.class);SettingService settings=mock(SettingService.class);
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any())).thenReturn(List.of());
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
        when(crypto.encrypt(any())).thenReturn("encrypted");
        WechatRefundEventService service=new WechatRefundEventService(jdbc,crypto,settings,new ObjectMapper());
        assertThat(service.handle(Map.of("Event","xpay_complaint_notify","ComplaintId","C1"))).containsEntry("ErrCode",0);
        verify(jdbc).update(contains("process_status=?"),any(Object[].class));
    }

    @Test void deliveredMatchingIosOrderIsRejectedAndMarkedForAppleReview() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);CryptoService crypto=mock(CryptoService.class);SettingService settings=mock(SettingService.class);
        Map<String,Object> row=new HashMap<>();
        row.put("order_id",10L);row.put("order_no","ORDER-10");row.put("order_status","success");row.put("payment_id",20L);
        row.put("delivery_status","delivered");row.put("delivered_at","2026-09-01 12:00:00");row.put("refund_status","none");
        row.put("service_snapshot_cipher","snapshot");row.put("openid","openid-10");row.put("wechat_order_type",7);
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any())).thenReturn(List.of());
        when(jdbc.queryForList(startsWith("select o.id"),any(Object[].class))).thenReturn(List.of(row));
        when(jdbc.queryForList(startsWith("select id"),any(Object[].class))).thenReturn(List.of());
        when(crypto.decrypt("snapshot")).thenReturn(Map.of("payment_product_id","PRODUCT-1"));
        when(crypto.encrypt(any())).thenReturn("encrypted");
        when(settings.value(anyString(),anyString())).thenAnswer(i->i.getArgument(1));
        WechatRefundEventService service=new WechatRefundEventService(jdbc,crypto,settings,new ObjectMapper());

        Map<String,Object> response=service.handle(Map.of("Event","xpay_subscribe_ios_refund_query_notify","pay_order_id","ORDER-10","channel_bill","APPLE-1","refund_time","1788254400","refund_request_reason","DID_NOT_LIKE","provide_status","1","product_id","PRODUCT-1"));

        assertThat(response).containsEntry("result_code",1);
        assertThat(response.get("evidence")).asString().contains("ORDER-10");
        verify(jdbc).update(contains("refund_status='apple_review'"),any(Object[].class));
    }

    @Test void mismatchedIosProductIsApprovedEvenWhenOrderWasDelivered() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);CryptoService crypto=mock(CryptoService.class);SettingService settings=mock(SettingService.class);
        Map<String,Object> row=new HashMap<>();
        row.put("order_id",11L);row.put("order_no","ORDER-11");row.put("order_status","success");row.put("payment_id",21L);
        row.put("delivery_status","delivered");row.put("refund_status","none");row.put("service_snapshot_cipher","snapshot");
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any())).thenReturn(List.of());
        when(jdbc.queryForList(startsWith("select o.id"),any(Object[].class))).thenReturn(List.of(row));
        when(jdbc.queryForList(startsWith("select id"),any(Object[].class))).thenReturn(List.of());
        when(crypto.decrypt("snapshot")).thenReturn(Map.of("payment_product_id","EXPECTED"));
        when(crypto.encrypt(any())).thenReturn("encrypted");
        when(settings.value(anyString(),anyString())).thenAnswer(i->i.getArgument(1));
        WechatRefundEventService service=new WechatRefundEventService(jdbc,crypto,settings,new ObjectMapper());

        Map<String,Object> response=service.handle(Map.of("Event","xpay_subscribe_ios_refund_query_notify","pay_order_id","ORDER-11","channel_bill","APPLE-2","refund_time","1788254400","provide_status",1,"product_id","OTHER"));

        assertThat(response).containsEntry("result_code",0);
        assertThat(response.get("evidence")).asString().contains("商品");
    }

    @Test void successfulRefundNotificationFinalizesOrderAndPayment() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);CryptoService crypto=mock(CryptoService.class);SettingService settings=mock(SettingService.class);
        Map<String,Object> row=new HashMap<>();
        row.put("order_id",12L);row.put("order_no","ORDER-12");row.put("order_status","refunding");row.put("payment_id",22L);
        row.put("refund_status","processing");row.put("refund_from_status","success");row.put("wechat_order_type",7);row.put("openid","openid-12");
        when(jdbc.query(anyString(),any(org.springframework.jdbc.core.RowMapper.class),any())).thenReturn(List.of());
        when(jdbc.queryForList(startsWith("select o.id"),any(Object[].class))).thenReturn(List.of(row));
        when(jdbc.queryForList(startsWith("select id"),any(Object[].class))).thenReturn(List.of());
        when(crypto.encrypt(any())).thenReturn("encrypted");
        WechatRefundEventService service=new WechatRefundEventService(jdbc,crypto,settings,new ObjectMapper());

        Map<String,Object> response=service.handle(Map.of("Event","xpay_refund_notify","WxRefundId","WX-R-1","MchRefundId","R-1","MchOrderId","ORDER-12","OpenId","openid-12","RefundFee",199,"RetCode",0,"RefundSuccTimestamp",1788254400));

        assertThat(response).containsEntry("ErrCode",0);
        verify(jdbc).update(startsWith("update ci_order set status='refunded'"),any(Object[].class));
        verify(jdbc).update(startsWith("update ci_payment set status='refunded'"),any(Object[].class));
    }
}

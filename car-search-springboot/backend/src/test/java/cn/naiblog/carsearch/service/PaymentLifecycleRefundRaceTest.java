package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.wechat.WechatPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PaymentLifecycleRefundRaceTest {
    @Test void completedRefundCannotBeReopenedByLateSubmitResponse()throws Exception{
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");var db=new JdbcTemplate(ds);
        db.execute("create table ci_user(id bigint primary key,openid varchar)");
        db.execute("create table ci_order(id bigint primary key,user_id bigint,order_no varchar,status varchar,amount decimal(10,2),updated_at timestamp)");
        db.execute("create table ci_payment(id bigint primary key,order_id bigint,order_no varchar,status varchar,pay_env int,delivery_status varchar,delivery_attempts int,delivery_attempted_at timestamp,refund_order_no varchar,refund_status varchar,refund_attempts int,refund_source varchar,refund_amount decimal(10,2),refund_reason varchar,refund_from_status varchar,refund_requested_at timestamp,wechat_order_type int,refund_last_error varchar,refund_payload varchar,refunded_at timestamp,updated_at timestamp)");
        db.update("insert into ci_user values(1,'LOCAL-USER')");db.update("insert into ci_order(id,user_id,order_no,status,amount) values(1,1,'LOCAL-ORDER','query_failed',6.90)");
        db.update("insert into ci_payment(id,order_id,order_no,status,pay_env,delivery_status,delivery_attempts,refund_status,refund_attempts,wechat_order_type) values(1,1,'LOCAL-ORDER','paid',0,'pending',0,'none',0,0)");
        var wx=mock(WechatPaymentService.class);var email=mock(EmailNotificationService.class);
        var service=new PaymentLifecycleService(db,wx,null,null,null,new ObjectMapper(),new DataSourceTransactionManager(ds),email);
        when(wx.queryOrder(anyString(),anyString(),anyInt())).thenAnswer(c->"LOCAL-ORDER".equals(c.getArgument(1))?Map.of("status",2,"order_type",0,"left_fee",690,"paid_fee",690):Map.of("status",5,"refund_fee",690));
        when(wx.refund(anyString(),anyString(),anyString(),anyInt(),anyInt(),anyString(),anyString(),anyInt())).thenAnswer(c->{
            // 另一个正常的退款状态核对已经确认完成，然后申请HTTP响应才返回。
            assertEquals("refunded",service.reconcileRefund("LOCAL-ORDER"));return Map.of("refund_order_id",c.getArgument(2));
        });
        assertEquals("refunded",service.requestRefund("LOCAL-ORDER","2"));
        assertEquals("refunded",db.queryForObject("select status from ci_order where id=1",String.class));
        assertEquals("refunded",db.queryForObject("select status from ci_payment where id=1",String.class));
        assertEquals("refunded",db.queryForObject("select refund_status from ci_payment where id=1",String.class));
        assertEquals(1,db.queryForObject("select refund_attempts from ci_payment where id=1",Integer.class));
        verify(wx,times(1)).refund(anyString(),anyString(),anyString(),anyInt(),anyInt(),anyString(),anyString(),anyInt());
        verify(email,times(1)).refundResult(any(),eq(true),anyString());
    }
}

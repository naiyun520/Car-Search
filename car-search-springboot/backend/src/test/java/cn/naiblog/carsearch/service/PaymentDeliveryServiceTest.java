package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.wechat.WechatPaymentService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentDeliveryServiceTest {
    private JdbcTemplate db;private WechatPaymentService wechat;private PaymentDeliveryService service;
    @BeforeEach void setup(){
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("create table ci_user(id bigint primary key,openid varchar)");
        db.execute("create table ci_order(id bigint primary key,user_id bigint,order_no varchar,status varchar,result_cipher varchar)");
        db.execute("create table ci_payment(id bigint primary key,order_id bigint,pay_env int,delivery_status varchar,delivery_attempts int,delivery_attempted_at timestamp(9),delivery_last_error varchar,delivered_at timestamp(9),updated_at timestamp(9))");
        db.update("insert into ci_user values(1,'LOCAL-USER')");db.update("insert into ci_order values(1,1,'LOCAL-ORDER','success','EXISTING-REPORT')");
        db.update("insert into ci_payment(id,order_id,pay_env,delivery_status,delivery_attempts) values(1,1,0,'pending',0)");
        wechat=mock(WechatPaymentService.class);service=new PaymentDeliveryService(db,wechat);
    }
    private void reportIsIntact(){assertEquals("success",db.queryForObject("select status from ci_order where id=1",String.class));assertEquals("EXISTING-REPORT",db.queryForObject("select result_cipher from ci_order where id=1",String.class));}
    @Test void shippingFailurePreservesTheSuccessfulReport(){
        doThrow(new IllegalStateException("微信发货接口调用失败：签名字段[pay_sig]为空")).when(wechat).deliver("LOCAL-ORDER",0);
        when(wechat.queryOrder("LOCAL-USER","LOCAL-ORDER",0)).thenReturn(Map.of("status",2));
        assertEquals("failed",service.deliver("LOCAL-ORDER",false));reportIsIntact();
        assertTrue(db.queryForObject("select delivery_last_error from ci_payment where id=1",String.class).contains("pay_sig"));
    }
    @Test void retryOnlyDeliveryAndAlreadyDeliveredOrderIsIdempotent(){
        db.update("update ci_payment set delivery_status='failed',delivery_attempts=1,delivery_last_error='LOCAL-OLD-ERROR'");
        assertEquals("delivered",service.deliver("LOCAL-ORDER",false));assertEquals("delivered",service.deliver("LOCAL-ORDER",false));
        verify(wechat,times(1)).deliver("LOCAL-ORDER",0);verify(wechat,never()).queryOrder(anyString(),anyString(),anyInt());reportIsIntact();
        assertEquals(2,db.queryForObject("select delivery_attempts from ci_payment where id=1",Integer.class));assertNull(db.queryForObject("select delivery_last_error from ci_payment where id=1",String.class));
    }
    @Test void timeoutCanBeConfirmedByWechatOrderStatusInsteadOfRequeryingBusiness(){
        doThrow(new IllegalStateException("LOCAL HTTP TIMEOUT")).when(wechat).deliver("LOCAL-ORDER",0);
        when(wechat.queryOrder("LOCAL-USER","LOCAL-ORDER",0)).thenReturn(Map.of("status",4));
        assertEquals("delivered",service.deliver("LOCAL-ORDER",false));reportIsIntact();assertEquals("delivered",service.deliver("LOCAL-ORDER",false));verify(wechat,times(1)).deliver("LOCAL-ORDER",0);
    }
    @Test void queryingOrderCannotSendDeliveryConfirmation(){
        db.update("update ci_order set status='querying'");assertThrows(BusinessException.class,()->service.deliver("LOCAL-ORDER",false));verifyNoInteractions(wechat);
    }
}

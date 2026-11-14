package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.service.AuditService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Entire suite uses a private in-memory database, never a configured application datasource. */
class AnalyticsControllerTest {
    JdbcTemplate db; AnalyticsController controller;
    @BeforeEach void setup(){
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("create table ci_user(id bigint,created_at timestamp)");
        db.execute("create table ci_order(id bigint,order_no varchar,user_id bigint,service_id int,service_name varchar,client_platform varchar,amount decimal(12,2),status varchar,created_at timestamp,paid_at timestamp,queried_at timestamp,cost_amount decimal(12,2),fulfillment_version int,provider_request_id varchar,provider_code varchar,result_cipher varchar,manual_refund_amount decimal(12,2),manual_refund_time timestamp)");
        db.execute("create table ci_payment(order_id bigint,refund_amount decimal(12,2),refunded_at timestamp,wechat_pay_transaction_id varchar,refund_from_status varchar)");
        db.execute("create table ci_provider_call(id bigint auto_increment,order_id bigint,service_id int default 1,provider_id int,stage varchar,call_status varchar,billing_status varchar,cost_amount decimal(12,2),provider_request_id varchar,business_code varchar,started_at timestamp,finished_at timestamp)");
        db.execute("create table ci_query_job(order_id bigint,state varchar)");
        controller=new AnalyticsController(db,mock(AuditService.class));
    }
    void order(int id,String created,String paid,String status,int version,String query){
        db.update("insert into ci_order(id,order_no,user_id,service_id,service_name,client_platform,amount,status,created_at,paid_at,queried_at,cost_amount,fulfillment_version,provider_request_id) values(?, ?,1,1,'测试服务','android',100,?,?,?,?,10,?,?)",id,"ORDER"+id,status,created,paid,query,version,"REQ"+id);
    }
    @SuppressWarnings("unchecked") Map<String,Object> current(){
        return (Map<String,Object>)controller.overview("2026-11-01","2026-11-03").data().get("current");
    }
    void amount(Object actual,String expected){assertEquals(0,new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())));}
    @Test void eventCashflowAndCreationCohortDoNotMix(){
        order(1,"2026-10-30 10:00:00","2026-11-01 10:00:00","success",2,"2026-11-01 11:00:00");
        order(2,"2026-11-01 10:00:00",null,"pending_payment",0,null);
        db.update("insert into ci_provider_call(order_id,billing_status,cost_amount,started_at) values(1,'confirmed',7,'2026-11-01 10:30:00')");
        db.update("insert into ci_provider_call(order_id,billing_status,cost_amount,started_at) values(1,'unknown',5,'2026-11-02 10:30:00')");
        db.update("insert into ci_payment(order_id,refund_amount,refunded_at,wechat_pay_transaction_id) values(1,100,'2026-11-02 11:00:00','transaction')");
        var m=current();
        amount(m.get("gross"),"100");amount(m.get("refunds"),"100");amount(m.get("cost"),"7");amount(m.get("unknown_cost"),"5");amount(m.get("profit"),"-7");
        amount(m.get("cohort_orders"),"1");amount(m.get("cohort_paid"),"0");amount(m.get("payment_conversion"),"0");
    }
    @Test void manualRefundUsesRefundDateAndDoesNotDuplicateOnlineRefund(){
        order(1,"2026-10-01 10:00:00","2026-10-01 11:00:00","refunded",0,"2026-10-01 12:00:00");
        db.update("update ci_order set manual_refund_amount=100,manual_refund_time='2026-11-02 10:00:00'");
        amount(current().get("refunds"),"100");
        db.update("insert into ci_payment(order_id,refund_amount,refunded_at,wechat_pay_transaction_id) values(1,100,'2026-11-02 11:00:00','wx')");
        amount(current().get("refunds"),"100");
    }
    @Test void dailyTotalsEqualSummaryAndEmptyDatesAreFilled(){
        order(1,"2026-11-01 10:00:00","2026-11-01 11:00:00","success",0,"2026-11-01 12:00:00");
        var result=controller.overview("2026-11-01","2026-11-03").data();
        var daily=(List<Map<String,Object>>)result.get("daily");
        assertEquals(3,daily.size());amount(daily.getFirst().get("cost"),"10");amount(daily.get(1).get("gross"),"0");
        var m=(Map<String,Object>)result.get("current");
        for(String k:List.of("gross","cost","refunds","net","profit","unknown_cost"))amount(daily.stream().map(x->new BigDecimal(x.get(k).toString())).reduce(BigDecimal.ZERO,BigDecimal::add),m.get(k).toString());
    }
    @Test void csvProtectsFormulasQuotesAndUsesBom(){
        assertEquals("\"'=SUM(1,2)\"",AnalyticsController.csvCell("=SUM(1,2)"));
        assertEquals("\"a\"\"b\"",AnalyticsController.csvCell("a\"b"));
        assertNull(AnalyticsController.ratio(1,0));
        var response=controller.export("2026-11-01","2026-11-03","daily",new MockHttpServletRequest());
        assertTrue(new String(response.getBody(),StandardCharsets.UTF_8).startsWith("\ufeff\"日期\""));
        assertThrows(BusinessException.class,()->controller.export("2026-11-01","2026-11-03","invalid",new MockHttpServletRequest()));
    }
    @Test void allExportTypesExecuteAgainstRealSql(){
        order(1,"2026-11-01 10:00:00","2026-11-01 11:00:00","success",0,"2026-11-01 12:00:00");
        for(String type:List.of("orders","services","calls")){
            var result=controller.export("2026-11-01","2026-11-03",type,new MockHttpServletRequest());
            assertEquals(200,result.getStatusCode().value());assertTrue(result.getBody().length>3);
        }
        assertEquals("\"-7\"",AnalyticsController.csvCell(new BigDecimal("-7")));
    }
    @Test void paymentReviewIsNotConfirmedRevenue(){
        order(1,"2026-11-01 10:00:00","2026-11-01 11:00:00","payment_review",0,null);
        amount(current().get("gross"),"0");amount(current().get("cohort_paid"),"0");
        amount(current().get("paid_orders"),"0");
    }
    @Test void serviceRenameKeepsAllOrders(){
        order(1,"2026-11-01 10:00:00","2026-11-01 11:00:00","success",0,"2026-11-01 12:00:00");
        order(2,"2026-11-02 10:00:00","2026-11-02 11:00:00","success",0,"2026-11-02 12:00:00");
        db.update("update ci_order set service_name='新名称' where id=2");
        var services=(List<Map<String,Object>>)controller.overview("2026-11-01","2026-11-03").data().get("services");
        assertEquals(1,services.size());amount(services.getFirst().get("orders"),"2");amount(services.getFirst().get("gross"),"200");
    }

    @Test void rangeIsInclusiveAndRejectsBadAndOversizedDates(){
        assertEquals(3,AnalyticsController.range("2026-11-01","2026-11-03").days());
        for(String[] dates:List.of(new String[]{"2026-11-04","2026-11-01"},new String[]{"bad","2026-11-01"},new String[]{"2020-01-01","2026-11-01"}))assertThrows(BusinessException.class,()->AnalyticsController.range(dates[0],dates[1]));
    }
    @Test void dashboardDefaultsToTodayInShanghaiAndStillAcceptsExplicitPeriods(){
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        var result=controller.overview(null,null).data();
        assertEquals(today.toString(),result.get("start"));assertEquals(today.toString(),result.get("end"));
        assertEquals(1,((List<?>)result.get("daily")).size());
        assertEquals(1,AnalyticsController.range(null,"2026-11-03").days());
        assertEquals(3,AnalyticsController.range("2026-11-01","2026-11-03").days());
    }
}


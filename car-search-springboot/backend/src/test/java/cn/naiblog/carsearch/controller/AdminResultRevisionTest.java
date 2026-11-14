package cn.naiblog.carsearch.controller;
import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.service.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminResultRevisionTest {
    JdbcTemplate db; AdminOrderController controller; TransactionTemplate tx;
    CryptoService crypto;
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        db.execute("create table ci_order(id bigint primary key,order_no varchar,status varchar,result_cipher varchar,active_result_revision_id bigint,updated_at timestamp)");
        db.execute("create table ci_order_result_revision(id bigint auto_increment primary key,order_id bigint,base_revision_id bigint,source varchar,result_cipher varchar,change_reason varchar,status varchar,created_by int,created_at timestamp,published_at timestamp)");
        db.update("insert into ci_order values(1,'ORDER','success','original',null,current_timestamp)");
        crypto=mock(CryptoService.class);when(crypto.encrypt(any())).thenReturn("encrypted-revision");
        controller=new AdminOrderController(db,crypto,mock(ObjectMapper.class),mock(PaymentReconciliationService.class),mock(PaymentLifecycleService.class),mock(QueryRunnerService.class),mock(QueryJobService.class),mock(AuditService.class),mock(EmailNotificationService.class));
    }
    Map<String,Object> body(){return Map.of("reason","更正展示","items",List.of(Map.of("key","vin","label","VIN","value","TEST")));}
    long draft(){return tx.execute(t->((Number)controller.createResultRevision("ORDER",body(),new MockHttpServletRequest()).data().get("id")).longValue());}
    @Test void draftDoesNotChangeOrderAndPublishPreservesOriginal(){
        long id=draft();
        assertEquals("original",db.queryForObject("select result_cipher from ci_order",String.class));
        tx.executeWithoutResult(t->controller.publishResultRevision("ORDER",id,new MockHttpServletRequest()));
        assertEquals("encrypted-revision",db.queryForObject("select result_cipher from ci_order",String.class));
        assertEquals("original",db.queryForObject("select result_cipher from ci_order_result_revision where source='original'",String.class));
        assertEquals("success",db.queryForObject("select status from ci_order",String.class));
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(t->controller.publishResultRevision("ORDER",id,new MockHttpServletRequest())));
    }
    @Test void staleDraftCannotOverwritePublishedResult(){
        long first=draft(),second=draft();
        tx.executeWithoutResult(t->controller.publishResultRevision("ORDER",first,new MockHttpServletRequest()));
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(t->controller.publishResultRevision("ORDER",second,new MockHttpServletRequest())));
    }
    @Test void failedOrderAndEmptyReasonsAreRejected(){
        var invalid=new HashMap<>(body());invalid.put("reason"," ");
        assertThrows(BusinessException.class,()->controller.createResultRevision("ORDER",invalid,new MockHttpServletRequest()));
        db.update("update ci_order set status='query_failed'");
        assertThrows(BusinessException.class,this::draft);
    }
    @Test void clearedResultsCannotBeResurrected(){
        long id=draft();db.update("update ci_order set result_cipher=null");
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(t->controller.publishResultRevision("ORDER",id,new MockHttpServletRequest())));
    }
    @Test void revisionDetailIsScopedToOrder(){
        long id=draft();
        db.update("insert into ci_order values(2,'OTHER','success','other',null,current_timestamp)");
        assertThrows(BusinessException.class,()->controller.revisionDetail("OTHER",id));
    }
}


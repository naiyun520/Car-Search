package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.config.SettingService;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 固定隔离库、真实MySQL连接；所有写入均落在连接自己的临时表。 */
@EnabledIfSystemProperty(named="car.local-db-audit",matches="true")
class CreatedIdConnectionAuditTest {
    @TempDir Path storage;
    Connection first,second;
    JdbcTemplate jdbc;
    AuditService audit;
    CryptoService crypto;
    ObjectMapper mapper=new ObjectMapper();
    @BeforeEach void setup()throws Exception{
        String url="jdbc:mysql://127.0.0.1:13316/car_search_unified_test?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        first=DriverManager.getConnection(url,"root","");
        second=DriverManager.getConnection(url,"root","");
        for(Connection connection:List.of(first,second)){
            try(var statement=connection.createStatement()){
                for(String table:List.of("ci_service","ci_service_category","ci_provider","ci_announcement")){
                    String ddl;
                    try(var definition=statement.executeQuery("show create table "+table)){
                        assertTrue(definition.next());ddl=definition.getString(2);
                    }
                    assertFalse(ddl.contains("FOREIGN KEY"),"Do not copy foreign keys into temporary audit tables");
                    statement.execute(ddl.replaceFirst("CREATE TABLE","CREATE TEMPORARY TABLE"));
                }
                statement.execute("insert into ci_service_category(id,code,name,icon,sort,status,created_at,updated_at) values(1,'seed','seed','grid',1,1,now(),now())");
                for(String table:List.of("ci_service","ci_service_category","ci_provider","ci_announcement"))
                    statement.execute("alter table "+table+" auto_increment="+(connection==first?501:1501));
                statement.execute("select last_insert_id("+(connection==first?9001:9002)+")");
            }
        }
        var a=new SingleConnectionDataSource(first,true);var b=new SingleConnectionDataSource(second,true);
        var counter=new AtomicInteger();
        var alternating=new AbstractDataSource(){
            @Override public Connection getConnection()throws SQLException{return(counter.getAndIncrement()%2==0?a:b).getConnection();}
            @Override public Connection getConnection(String user,String password)throws SQLException{return getConnection();}
        };
        jdbc=new JdbcTemplate(alternating);
        audit=mock(AuditService.class);crypto=mock(CryptoService.class);
        when(crypto.encrypt(any())).thenReturn("LOCAL-TEST-CIPHER");
    }
    @AfterEach void close()throws Exception{if(first!=null)first.close();if(second!=null)second.close();}
    int actualId(String table,String field,String value)throws Exception{
        List<Integer> found=new ArrayList<>();
        for(Connection connection:List.of(first,second)){
            try(var statement=connection.prepareStatement("select id from "+table+" where "+field+"=?")){
                statement.setString(1,value);
                try(var rows=statement.executeQuery()){while(rows.next())found.add(rows.getInt(1));}
            }
        }
        assertEquals(1,found.size());return found.getFirst();
    }
    void matchesActual(int returned,String table,String field,String value,String action,String type)throws Exception{
        assertEquals(actualId(table,field,value),returned);
        assertNotEquals(9001,returned);assertNotEquals(9002,returned);
        verify(audit).record(any(),eq(action),eq(type),eq(String.valueOf(returned)),any());
        System.out.println("R26 regression passed: "+type+" response matches actual inserted ID="+returned);
    }
    static Map<String,Object> serviceBody(String code){
        Map<String,Object> body=new HashMap<>();
        body.put("code",code);body.put("name","Local service");body.put("short_name","Local");
        body.put("category_id",1);body.put("builtin_icon","car");body.put("status",0);
        body.put("provider_api_id",0);body.put("api_url","https://example.invalid/not-called");
        body.put("sale_price_android","1.00");body.put("sale_price_ios","1.00");body.put("cost_price","0");
        body.put("input_schema",List.of(Map.of("key","vin","label","VIN","type","vin","required",true)));
        body.put("result_schema",List.of(Map.of("key","name","label","Name")));
        return body;
    }
    @Test void categoryReturnsItsOwnGeneratedIdAcrossConnectionChanges()throws Exception{
        var controller=new ServiceCategoryAdminController(jdbc,audit);
        int returned=((Number)controller.create(Map.of("code","audit_category","name","Local","icon","grid"),new MockHttpServletRequest()).data().get("id")).intValue();
        matchesActual(returned,"ci_service_category","code","audit_category","service_category.create","service_category");
    }
    @Test void providerReturnsItsOwnGeneratedIdAcrossConnectionChanges()throws Exception{
        var controller=new ProviderAdminController(jdbc,crypto,audit,mock(ProviderBalanceService.class));
        int returned=((Number)controller.create(Map.of("code","audit_provider","name","Local","auth_type","none","base_url","https://example.invalid/","status",0),new MockHttpServletRequest()).data().get("id")).intValue();
        matchesActual(returned,"ci_provider","code","audit_provider","provider.create","provider");
    }
    @Test void announcementReturnsItsOwnGeneratedIdAcrossConnectionChanges()throws Exception{
        var controller=new AdminController(jdbc,mock(SettingService.class),audit,mock(MailService.class));
        int returned=((Number)controller.announcement(Map.of("title","Local audit title","content","Local audit content"),new MockHttpServletRequest()).data().get("id")).intValue();
        matchesActual(returned,"ci_announcement","title","Local audit title","announcement.save","announcement");
    }
    @Test void serviceReturnsItsOwnGeneratedIdAcrossConnectionChanges()throws Exception{
        var controller=new ServiceAdminController(jdbc,crypto,mapper,mock(ProviderService.class),audit,storage.toString(),mock(QueryJobService.class));
        int returned=((Number)controller.create(serviceBody("audit_service"),new MockHttpServletRequest()).data().get("id")).intValue();
        matchesActual(returned,"ci_service","code","audit_service","service.create","service");
    }
}

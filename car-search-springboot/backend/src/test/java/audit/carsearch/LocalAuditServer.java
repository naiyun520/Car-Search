package audit.carsearch;

import cn.naiblog.carsearch.CarSearchApplication;
import cn.naiblog.carsearch.config.SettingService;
import cn.naiblog.carsearch.security.*;
import cn.naiblog.carsearch.service.*;
import cn.naiblog.carsearch.wechat.*;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 测试专用启动器，不进入生产JAR；只接受硬编码的本机审计库。 */
public class LocalAuditServer {
    public static final String DB="car_search_audit_20261104";
    public static final String URL="jdbc:mysql://127.0.0.1:13316/"+DB+"?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    public static void main(String[] args)throws Exception{
        System.setProperty("car.local-test-only","true");
        Path runtime=Path.of(System.getProperty("audit.runtime")).toAbsolutePath().normalize();
        if(!Files.isRegularFile(runtime.resolve("baseline.json")))throw new IllegalStateException("Prepare audit clone first");
        var context=new SpringApplicationBuilder(CarSearchApplication.class,Fixtures.class).run(
            "--server.address=127.0.0.1","--server.port=18082","--spring.profiles.active=audit",
            "--spring.datasource.url="+URL,"--spring.datasource.username=root","--spring.datasource.password=",
            "--car.local-test-only=true","--car.dev-login-enabled=false","--car.storage-path="+runtime.resolve("uploads"),
            "--car.query-job-delay-ms=10000","--car.query-job-initial-delay-ms=5000",
            "--car.email-delivery-delay-ms=1000","--car.email-delivery-initial-delay-ms=5000",
            "--car.reconcile-initial-delay-ms=86400000","--car.provider-balance-initial-delay-ms=86400000",
            "--car.cleanup-cron=-");
        var jdbc=context.getBean(JdbcTemplate.class);
        if(!context.getBean(HikariDataSource.class).getJdbcUrl().equals(URL))throw new IllegalStateException("Wrong database");
        var settings=context.getBean(SettingService.class);
        Map<String,String> flags=new LinkedHashMap<>();
        flags.put("payment_enabled","1");flags.put("android_auto_refund_query_failed","0");
        flags.put("email_notify_enabled","1");flags.put("email_event_payment_success","1");
        flags.put("email_event_query_success","1");flags.put("email_event_query_failed","1");flags.put("email_event_refund_result","1");
        flags.put("email_recipients","audit@example.invalid");flags.put("smtp_host","127.0.0.1");
        flags.put("smtp_from_address","audit@example.invalid");flags.put("admin_path","localqa");
        flags.put("wechat_app_id","wx1234567890abcdef");flags.put("wechat_offer_id","1234567890");flags.put("wechat_pay_env","0");
        flags.forEach(settings::saveValue);
        for(String key:List.of("wechat_app_secret","wechat_production_app_key","wechat_sandbox_app_key"))
            settings.saveSecure(key,"AUDIT-ONLY-NEVER-A-REAL-SECRET");
        settings.saveSecure("wechat_message_token","AuditMessageToken");
        settings.saveSecure("wechat_message_aes_key",Base64.getEncoder().withoutPadding().encodeToString(new byte[32]));
        jdbc.update("update ci_provider set balance_enabled=0");
        jdbc.update("update ci_email_delivery set status='skipped' where status<>'sent'");
        jdbc.update("delete from ci_admin_token");
        jdbc.update("delete from ci_admin_login_attempt");
        String password="Audit-Only-2026!";
        jdbc.update("update ci_admin set password_hash=?,must_change_password=0,status=1",new BCryptPasswordEncoder().encode(password));
        String username=jdbc.queryForObject("select username from ci_admin where status=1 order by id limit 1",String.class);
        Map<String,Object> state=new LinkedHashMap<>();
        state.put("base","http://127.0.0.1:18082");state.put("database",DB);state.put("adminUsername",username);state.put("adminPassword",password);
        state.put("pid",ProcessHandle.current().pid());state.put("readyAt",Instant.now().toString());state.put("gateway","in-process test doubles");
        state.put("messageToken","AuditMessageToken");state.put("messageAes",Base64.getEncoder().withoutPadding().encodeToString(new byte[32]));state.put("appId","wx1234567890abcdef");
        Files.writeString(runtime.resolve("server.json"),context.getBean(ObjectMapper.class).writeValueAsString(state));
        System.out.println("AUDIT_SERVER_READY port=18082 database="+DB+" external payment/provider calls mocked");
    }

    @TestConfiguration(proxyBeanMethods=false)
    public static class Fixtures {
        @Bean Gateway auditGateway(JdbcTemplate jdbc,SettingService settings)throws Exception{return new Gateway(jdbc,settings);}
        @Bean @Primary ProviderService auditProvider(Gateway g){return g.provider;}
        @Bean @Primary WechatPaymentService auditWechat(Gateway g){return g.wechat;}
        @Bean @Primary WechatLoginService auditLogin(Gateway g){return g.login;}
        @Bean @Primary MailService auditMail(Gateway g){return g.mail;}
        @Bean Control auditControl(Gateway g,JdbcTemplate jdbc,WechatMessageCryptoService message,HikariDataSource ds){return new Control(g,jdbc,message,ds);}
    }

    public static class Gateway {
        final JdbcTemplate jdbc;final SettingService settings;
        final ProviderService provider=mock(ProviderService.class);
        final WechatPaymentService wechat=mock(WechatPaymentService.class);
        final WechatLoginService login=mock(WechatLoginService.class);
        final MailService mail=mock(MailService.class);
        final ConcurrentMap<String,String> scenarios=new ConcurrentHashMap<>(),requestScenarios=new ConcurrentHashMap<>();
        final ConcurrentMap<String,Map<String,Object>> paid=new ConcurrentHashMap<>(),refunds=new ConcurrentHashMap<>();
        final ConcurrentMap<String,AtomicInteger> counters=new ConcurrentHashMap<>();
        final AtomicInteger active=new AtomicInteger(),maxActive=new AtomicInteger(),serial=new AtomicInteger();
        final String instance=Long.toString(System.currentTimeMillis(),36);
        volatile int providerDelay=20;volatile boolean mailFails=false;
        Gateway(JdbcTemplate jdbc,SettingService settings)throws Exception{
            this.jdbc=jdbc;this.settings=settings;
            when(login.codeToSession(anyString())).thenAnswer(call->{
                String code=call.getArgument(0);
                if(code.startsWith("FAIL"))throw new cn.naiblog.carsearch.api.BusinessException("模拟登录凭证失效");
                return new WechatLoginService.Session("audit-user-"+code,"",Base64.getEncoder().encodeToString(new byte[16]),0,"audit");
            });
            when(wechat.queryOrder(anyString(),anyString(),anyInt())).thenAnswer(call->queryOrder(call.getArgument(0),call.getArgument(1)));
            doAnswer(call->{count("DELIVERY:"+call.getArgument(0));return null;}).when(wechat).deliver(anyString(),anyInt());
            when(wechat.refund(anyString(),anyString(),anyString(),anyInt(),anyInt(),anyString(),anyString(),anyInt())).thenAnswer(call->{
                String no=call.getArgument(1),refundNo=call.getArgument(2);count("REFUND:"+no);
                Thread.sleep(20);
                if("refund_error".equals(scenarios.get(no)))throw new IllegalStateException("微信支付接口调用失败：模拟拒绝");
                refunds.put(refundNo,Map.of("order_id",refundNo,"status",5,"refund_fee",call.getArgument(4)));
                return Map.of("refund_order_id",refundNo);
            });
            when(provider.executeDirect(anyMap(),anyMap())).thenAnswer(call->{
                Map<String,Object> input=call.getArgument(1);
                String scenario=scenarios.getOrDefault(String.valueOf(input.get("vin")),"success");
                enter("DIRECT");
                try{
                    if(scenario.equals("failed"))throw new ProviderQueryException("模拟未查到信息","404","");
                    if(scenario.equals("throw"))throw new java.net.SocketTimeoutException("模拟供应商超时");
                    return new ProviderService.Result("200","AUDIT-D-"+instance+"-"+serial.incrementAndGet(),items(input));
                }finally{active.decrementAndGet();}
            });
            when(provider.submitAsync(anyMap(),anyMap())).thenAnswer(call->{
                Map<String,Object> input=call.getArgument(1);
                String scenario=scenarios.getOrDefault(String.valueOf(input.get("vin")),"success");
                enter("SUBMIT");
                try{
                    if(scenario.equals("submit_unknown"))throw new java.net.SocketTimeoutException("模拟下单响应不确定");
                    if(scenario.equals("submit_failed"))throw new ProviderQueryException("模拟下单拒绝","400","");
                    String id="AUDIT-S-"+instance+"-"+serial.incrementAndGet();requestScenarios.put(id,scenario);
                    return new ProviderService.SubmitResult("200",id);
                }finally{active.decrementAndGet();}
            });
            when(provider.fetchReport(anyMap(),anyString())).thenAnswer(call->{
                String id=call.getArgument(1),scenario=requestScenarios.getOrDefault(id,"success");
                enter("FETCH");count("FETCH:"+id);
                try{
                    if(scenario.equals("fetch_throw"))throw new java.net.SocketTimeoutException("模拟取报告网络异常");
                    if(scenario.equals("pending"))return new ProviderService.FetchResult(ProviderService.FetchState.RUNNING,"200",id,List.of(),"处理中");
                    if(scenario.equals("failed"))return new ProviderService.FetchResult(ProviderService.FetchState.FAILED,"404",id,List.of(),"未查询到信息");
                    if(scenario.equals("empty"))return new ProviderService.FetchResult(ProviderService.FetchState.NO_DATA,"200",id,List.of(),"没有可展示结果");
                    return new ProviderService.FetchResult(ProviderService.FetchState.SUCCESS,"200",id,items(Map.of("vin","AUDIT")),"");
                }finally{active.decrementAndGet();}
            });
            when(provider.queryBalance(anyMap())).thenReturn(new ProviderService.BalanceResult(new BigDecimal("123.45"),"200"));
            when(mail.layout(anyString(),anyString())).thenAnswer(call->call.getArgument(1));
            when(mail.testTemplate()).thenReturn("<html>Local audit mail</html>");
            doAnswer(call->{if(mailFails)throw new IllegalStateException("模拟邮件连接失败");return null;}).when(mail).testConnection();
            when(mail.available()).thenAnswer(call->!settings.value("smtp_host","").isBlank()&&!settings.value("smtp_from_address","").isBlank());
            when(mail.recipients()).thenAnswer(call->Arrays.stream(settings.value("email_recipients","").split("[,;]")).filter(s->!s.isBlank()).toList());
            doAnswer(call->{count("MAIL");if(mailFails)throw new IllegalStateException("模拟邮件服务器暂不可用");return null;}).when(mail).send(anyString(),anyString(),anyString());
        }
        List<Map<String,Object>> items(Map<String,Object> input){return List.of(Map.of("key","audit","label","查询结果","value","本地模拟报告"),Map.of("key","vin","label","VIN","value",String.valueOf(input.getOrDefault("vin","AUDIT"))));}
        void enter(String stage)throws InterruptedException{count(stage);int n=active.incrementAndGet();maxActive.accumulateAndGet(n,Math::max);Thread.sleep(providerDelay);}
        void count(String name){counters.computeIfAbsent(name,k->new AtomicInteger()).incrementAndGet();}
        Map<String,Object> queryOrder(String openid,String no){
            count("PAY_QUERY");
            if(refunds.containsKey(no))return refunds.get(no);
            List<Map<String,Object>> rows=jdbc.queryForList("select o.*,u.openid from ci_order o join ci_user u on u.id=o.user_id where o.order_no=?",no);
            if(rows.isEmpty())return Map.of("not_found",true);
            Map<String,Object> row=rows.getFirst();
            if(!openid.equals(String.valueOf(row.get("openid"))))return Map.of("not_found",true);
            Map<String,Object> supplied=paid.get(no);
            if(supplied==null)return Map.of("order_id",no,"status",1);
            int fee=new BigDecimal(String.valueOf(row.get("amount"))).movePointRight(2).intValue();
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("order_id",no);out.put("status",2);out.put("order_type","ios".equals(row.get("client_platform"))?7:0);
            out.put("paid_fee",fee);out.put("order_fee",fee);out.put("left_fee",fee);out.put("env_type",1);
            out.put("wx_order_id","AUDIT-WX-"+no);out.put("wxpay_order_id","AUDIT-TX-"+no);out.putAll(supplied);return out;
        }
    }

    @RestController
    @RequestMapping("/__audit")
    public static class Control {
        final Gateway g;final JdbcTemplate jdbc;final WechatMessageCryptoService message;final HikariDataSource ds;
        Control(Gateway g,JdbcTemplate jdbc,WechatMessageCryptoService message,HikariDataSource ds){this.g=g;this.jdbc=jdbc;this.message=message;this.ds=ds;}
        @PostMapping("/pay/{no}") public Object pay(@PathVariable String no,@RequestBody(required=false)Map<String,Object> body){g.paid.put(no,body==null?Map.of():body);return Map.of("ok",true);}
        @PostMapping("/scenario") public Object scenario(@RequestBody Map<String,Object> body){
            g.scenarios.put(String.valueOf(body.get("key")),String.valueOf(body.get("value")));return Map.of("ok",true);
        }
        @PostMapping("/mail-fail") public Object mailFail(@RequestBody Map<String,Object> body){g.mailFails=Boolean.TRUE.equals(body.get("enabled"));return Map.of("ok",true);}
        @PostMapping("/provider-delay") public Object delay(@RequestBody Map<String,Object> body){g.providerDelay=Math.max(0,Math.min(5000,((Number)body.get("ms")).intValue()));return Map.of("ok",true);}
        @PostMapping("/encode-message") public Object encode(@RequestBody Map<String,Object> body)throws Exception{return message.response(body);}
        @GetMapping("/metrics") public Object metrics(){
            var operating=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
            var heap=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("time",System.currentTimeMillis());result.put("cpuNanos",operating.getProcessCpuTime());
            result.put("heapUsed",heap.getUsed());result.put("heapCommitted",heap.getCommitted());result.put("heapMax",heap.getMax());
            result.put("threads",ManagementFactory.getThreadMXBean().getThreadCount());result.put("peakThreads",ManagementFactory.getThreadMXBean().getPeakThreadCount());
            result.put("providerActive",g.active.get());result.put("providerPeak",g.maxActive.get());
            Map<String,Integer> calls=new TreeMap<>();g.counters.forEach((key,value)->calls.put(key,value.get()));result.put("calls",calls);
            var pool=ds.getHikariPoolMXBean();
            result.put("dbActive",pool.getActiveConnections());result.put("dbIdle",pool.getIdleConnections());result.put("dbWaiting",pool.getThreadsAwaitingConnection());
            return result;
        }
    }
}

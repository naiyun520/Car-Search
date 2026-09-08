package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.config.SettingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class QueryRunnerService {
    private final JdbcTemplate jdbc;private final CryptoService crypto;private final ProviderService provider;private final PaymentDeliveryService delivery;private final SettingService settings;private final MailService mail;
    public QueryRunnerService(JdbcTemplate jdbc,CryptoService crypto,ProviderService provider,PaymentDeliveryService delivery,SettingService settings,MailService mail){this.jdbc=jdbc;this.crypto=crypto;this.provider=provider;this.delivery=delivery;this.settings=settings;this.mail=mail;}
    public Map<String,Object> run(Map<String,Object> order){String status=String.valueOf(order.get("status"));if(List.of("success","query_failed","querying").contains(status)||!"paid".equals(status))return Map.of("status",status);int claimed=jdbc.update("update ci_order set status='querying',query_attempts=query_attempts+1,query_last_error=null,updated_at=? where id=? and status='paid'",LocalDateTime.now(),order.get("id"));if(claimed==0)return Map.of("status",String.valueOf(jdbc.queryForObject("select status from ci_order where id=?",String.class,order.get("id"))));
        Map<String,Object> service=orderService(order);if(service==null){fail(order,"error","","订单服务快照不可用");notifyFailure(order,"订单服务快照不可用");return Map.of("status","query_failed");}
        try{ProviderService.Result r=provider.query(service,crypto.decrypt(String.valueOf(order.get("input_cipher"))));LocalDateTime now=LocalDateTime.now();jdbc.update("update ci_order set status='success',provider_code=?,provider_request_id=?,query_last_error=null,result_cipher=?,queried_at=?,updated_at=? where id=?",r.code(),empty(r.requestId()),crypto.encrypt(Map.of("items",r.data())),now,now,order.get("id"));}
        catch(Exception e){String code=e instanceof ProviderQueryException p?p.providerCode():"error";String rid=e instanceof ProviderQueryException p?p.requestId():"";String message=truncate(e.getMessage(),500);fail(order,code,rid,message);notifyFailure(order,message);return Map.of("status","query_failed");}
        try{delivery.deliver(String.valueOf(order.get("order_no")),false);}catch(Exception ignored){}
        try{if("1".equals(settings.value("email_notify_query_success","0"))){Map<String,Object>fresh=jdbc.queryForMap("select * from ci_order where id=?",order.get("id"));mail.notifyAll("查询成功 - "+order.get("order_no"),mail.orderTemplate("查询成功通知",fresh,"供应商查询已成功完成"));}}catch(Exception ignored){}
        return Map.of("status","success");}
    public Map<String,Object> retry(String no){Map<String,Object> o=jdbc.queryForMap("select * from ci_order where order_no=?",no);if("query_failed".equals(o.get("status")))jdbc.update("update ci_order set status='paid',updated_at=? where id=? and status='query_failed'",LocalDateTime.now(),o.get("id"));return run(jdbc.queryForMap("select * from ci_order where id=?",o.get("id")));}
    private Map<String,Object> orderService(Map<String,Object> o){String cipher=String.valueOf(o.getOrDefault("service_snapshot_cipher",""));if(!cipher.isBlank()){Map<String,Object>s=crypto.decrypt(cipher);return s.containsKey("request_url_cipher")?s:null;}List<Map<String,Object>>rows=jdbc.queryForList("select * from ci_service where id=?",o.get("service_id"));return rows.isEmpty()?null:rows.getFirst();}
    private void fail(Map<String,Object>o,String code,String rid,String msg){LocalDateTime now=LocalDateTime.now();jdbc.update("update ci_order set status='query_failed',provider_code=?,provider_request_id=?,query_last_error=?,queried_at=?,updated_at=? where id=?",truncate(code,20),rid.isBlank()?null:truncate(rid,100),msg,now,now,o.get("id"));}
    private void notifyFailure(Map<String,Object>order,String message){try{if("1".equals(settings.value("email_notify_query_failed","0"))){Map<String,Object>fresh=jdbc.queryForMap("select * from ci_order where id=?",order.get("id"));mail.notifyAll("查询异常 - "+order.get("order_no"),mail.orderTemplate("查询异常通知",fresh,message));}}catch(Exception ignored){}}
    private String empty(String s){return s==null||s.isBlank()?null:s;}private String truncate(String s,int n){if(s==null)return"";return s.codePointCount(0,s.length())<=n?s:s.substring(0,s.offsetByCodePoints(0,n));}
}

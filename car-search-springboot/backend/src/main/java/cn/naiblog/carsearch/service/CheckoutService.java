package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.security.CryptoService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CheckoutService {
    private final JdbcTemplate jdbc; private final CryptoService crypto; private final ObjectMapper mapper; private final VirtualPaymentService payment; private final QueryRunnerService queries; private final PaymentReconciliationService reconciliation;private final PaymentDeliveryService delivery;
    private final SecureRandom random=new SecureRandom();
    public CheckoutService(JdbcTemplate jdbc,CryptoService crypto,ObjectMapper mapper,VirtualPaymentService payment,QueryRunnerService queries,PaymentReconciliationService reconciliation,PaymentDeliveryService delivery){this.jdbc=jdbc;this.crypto=crypto;this.mapper=mapper;this.payment=payment;this.queries=queries;this.reconciliation=reconciliation;this.delivery=delivery;}

    @Transactional
    public Map<String,Object> create(Map<String,Object> user,String serviceCode,String requestKey,Map<String,Object> payload){
        List<Map<String,Object>> old=jdbc.queryForList("select * from ci_order where user_id=? and request_key=? limit 1",user.get("id"),requestKey);if(!old.isEmpty())return result(old.getFirst(),null,true);
        if(!Boolean.TRUE.equals(payload.get("accepted")))throw new BusinessException("请先阅读并同意授权协议与免责声明");
        List<Map<String,Object>> services=jdbc.queryForList("select * from ci_service where code=? and status=1 limit 1",serviceCode);if(services.isEmpty())throw new BusinessException("该服务当前不可用，请返回首页刷新服务目录");payment.ensureAvailable();Map<String,Object> service=services.getFirst();
        try {List<Map<String,Object>> schema=mapper.readValue(String.valueOf(service.get("input_schema")),new TypeReference<>(){});Map<String,Object> input=InputValidator.validate(schema,castMap(payload.get("input")));
            LocalDateTime now=LocalDateTime.now();String orderNo=orderNo(((Number)user.get("id")).longValue());String summary=input.values().stream().map(v->mask(String.valueOf(v))).reduce((a,b)->a+" / "+b).orElse("");
            Map<String,Object> order=new LinkedHashMap<>();order.put("order_no",orderNo);order.put("amount",service.get("sale_price"));Map<String,Object> clientPayment=payment.clientParams(order,user,service);
            try {jdbc.update("""
                    insert into ci_order(order_no,request_key,user_id,service_id,service_name,amount,cost_amount,status,input_cipher,service_snapshot_cipher,input_summary,query_attempts,expired_at,created_at,updated_at)
                    values(?,?,?,?,?,?,?,'pending_payment',?,?,?,?,?,?,?)
                    """,orderNo,requestKey,user.get("id"),service.get("id"),service.get("name"),service.get("sale_price"),service.get("cost_price"),crypto.encrypt(input),crypto.encrypt(service),truncate(summary,100),0,now.plusMinutes(30),now,now);}
            catch(DuplicateKeyException duplicate){List<Map<String,Object>>concurrent=jdbc.queryForList("select * from ci_order where user_id=? and request_key=? limit 1",user.get("id"),requestKey);if(!concurrent.isEmpty())return result(concurrent.getFirst(),null,true);throw duplicate;}
            Long id=jdbc.queryForObject("select id from ci_order where order_no=?",Long.class,orderNo);jdbc.update("insert into ci_payment(order_id,order_no,amount,pay_env,status,delivery_status,refund_status,created_at,updated_at) values(?,?,?,?,\'created\',\'pending\',\'none\',?,?)",id,orderNo,service.get("sale_price"),settingsEnv(clientPayment),now,now);
            return result(jdbc.queryForMap("select * from ci_order where id=?",id),clientPayment,false);
        }catch(BusinessException|IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("订单创建失败",e);}
    }
    public Map<String,Object> status(long userId,String no){Map<String,Object>o=owned(userId,no);if(List.of("pending_payment","payment_review").contains(String.valueOf(o.get("status"))))reconciliation.reconcile(o,userRow(userId));return detail(owned(userId,no));}
    public Map<String,Object> payment(long userId,String no){Map<String,Object>o=owned(userId,no);if(!"pending_payment".equals(o.get("status")))return paymentResult(detail(o),null);Map<String,Object>u=userRow(userId);reconciliation.reconcile(o,u);o=owned(userId,no);if(!"pending_payment".equals(o.get("status")))return paymentResult(detail(o),null);Object exp=o.get("expired_at");if(exp instanceof java.sql.Timestamp t&&t.toLocalDateTime().isBefore(LocalDateTime.now())){jdbc.update("update ci_order set status='cancelled',updated_at=? where id=? and status='pending_payment'",LocalDateTime.now(),o.get("id"));o.put("status","cancelled");return paymentResult(detail(o),null);}Map<String,Object>s=crypto.decrypt(String.valueOf(o.getOrDefault("service_snapshot_cipher","")));if(s.isEmpty())throw new IllegalStateException("订单支付配置快照不可用，请联系客服处理");return paymentResult(safe(o),payment.clientParams(o,u,s));}
    public Map<String,Object> query(long userId,String no){Map<String,Object> o=owned(userId,no);if("paid".equals(o.get("status")))queries.run(o);return detail(owned(userId,no));}
    public Object recoverable(long userId){List<Map<String,Object>> rows=jdbc.queryForList("select * from ci_order where user_id=? and paid_at is not null and status in ('paid','querying','query_failed','success','payment_review') and created_at>=? order by id desc limit 1",userId,LocalDateTime.now().minusHours(24));return rows.isEmpty()?null:detail(rows.getFirst());}
    public Map<String,Object> safe(Map<String,Object> o){Map<String,Object> r=new LinkedHashMap<>();for(String k:List.of("order_no","service_name","status","paid_at","queried_at","created_at"))r.put(k,o.get(k));return r;}
    public Map<String,Object> detail(Map<String,Object> o){Map<String,Object> r=safe(o);if("success".equals(o.get("status"))&&o.get("result_cipher")!=null){r.put("result",crypto.decrypt(String.valueOf(o.get("result_cipher"))).getOrDefault("items",List.of()));try{delivery(o);}catch(Exception ignored){}}if("query_failed".equals(o.get("status")))r.put("message","查询未成功，请联系客服处理");if("payment_review".equals(o.get("status")))r.put("message","支付信息需要人工核对，请勿重复支付并联系客服");return r;}
    private void delivery(Map<String,Object>o){delivery.deliver(String.valueOf(o.get("order_no")),false);}
    private Map<String,Object> owned(long uid,String no){List<Map<String,Object>> rows=jdbc.queryForList("select * from ci_order where order_no=? and user_id=? limit 1",no,uid);if(rows.isEmpty())throw new BusinessException("订单不存在或无权访问");return rows.getFirst();}
    private Map<String,Object> result(Map<String,Object> o,Object p,boolean reused){Map<String,Object> r=new LinkedHashMap<>();r.put("order",safe(o));r.put("payment",p);r.put("reused",reused);return r;}
    private Map<String,Object> paymentResult(Object o,Object p){Map<String,Object>r=new LinkedHashMap<>();r.put("order",o);r.put("payment",p);return r;}
    private Map<String,Object> userRow(long id){return jdbc.queryForMap("select * from ci_user where id=?",id);}
    @SuppressWarnings("unchecked") private Map<String,Object> castMap(Object o){return o instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();}
    private String orderNo(long uid){byte[] b=new byte[4];random.nextBytes(b);return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"))+String.format("%06d",uid%1_000_000)+HexFormat.of().withUpperCase().formatHex(b);}
    private String mask(String s){int n=s.codePointCount(0,s.length());if(n<=2)return "*".repeat(n);return s.substring(0,s.offsetByCodePoints(0,1))+"*".repeat(Math.min(6,n-2))+s.substring(s.offsetByCodePoints(0,n-1));}
    private String truncate(String s,int n){return s.codePointCount(0,s.length())<=n?s:s.substring(0,s.offsetByCodePoints(0,n));}
    private int settingsEnv(Map<String,Object> p){try{return ((Number)mapper.readValue(String.valueOf(p.get("signData")),Map.class).getOrDefault("env",0)).intValue();}catch(Exception e){return 0;}}
}

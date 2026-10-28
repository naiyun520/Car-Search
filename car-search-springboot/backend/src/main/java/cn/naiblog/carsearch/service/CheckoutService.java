package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.config.SettingService;
import cn.naiblog.carsearch.security.CryptoService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CheckoutService {
    private final JdbcTemplate jdbc; private final CryptoService crypto; private final ObjectMapper mapper; private final VirtualPaymentService payment; private final QueryRunnerService queries; private final PaymentReconciliationService reconciliation;private final PaymentDeliveryService delivery;private final SettingService settings;
    private final SecureRandom random=new SecureRandom();
    /** 一级（单次查询）结果保留时长：从结果生成时间(queried_at)起 24 小时，到期后不再返回结果 */
    private static final long RESULT_RETENTION_HOURS=24;
    private static final DateTimeFormatter MINUTE_FORMAT=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public CheckoutService(JdbcTemplate jdbc,CryptoService crypto,ObjectMapper mapper,VirtualPaymentService payment,QueryRunnerService queries,PaymentReconciliationService reconciliation,PaymentDeliveryService delivery,SettingService settings){this.jdbc=jdbc;this.crypto=crypto;this.mapper=mapper;this.payment=payment;this.queries=queries;this.reconciliation=reconciliation;this.delivery=delivery;this.settings=settings;}

    @Transactional
    public Map<String,Object> create(Map<String,Object> user,String serviceCode,String requestKey,Map<String,Object> payload){
        List<Map<String,Object>> old=jdbc.queryForList("select * from ci_order where user_id=? and request_key=? limit 1",user.get("id"),requestKey);if(!old.isEmpty())return result(old.getFirst(),null,true);
        if(!Boolean.TRUE.equals(payload.get("accepted")))throw new BusinessException("请先阅读并同意授权协议与免责声明");
        List<Map<String,Object>> services=jdbc.queryForList("select * from ci_service where code=? and status=1 limit 1",serviceCode);if(services.isEmpty())throw new BusinessException("该服务当前不可用，请返回首页刷新服务目录");payment.ensureAvailable();Map<String,Object> service=services.getFirst();
        try {String platform=payment.normalizePlatform(payload.get("client_platform"));List<Map<String,Object>> schema=mapper.readValue(String.valueOf(service.get("input_schema")),new TypeReference<>(){});Map<String,Object> input=InputValidator.validate(schema,castMap(payload.get("input")));
            LocalDateTime now=LocalDateTime.now();String orderNo=orderNo(((Number)user.get("id")).longValue());String summary=input.values().stream().map(v->mask(String.valueOf(v))).reduce((a,b)->a+" / "+b).orElse("");
            Object selectedPrice=payment.priceFor(service,platform);Map<String,Object> order=new LinkedHashMap<>();order.put("order_no",orderNo);order.put("amount",selectedPrice);order.put("client_platform",platform);Map<String,Object> clientPayment=payment.clientParams(order,user,service);
            try {jdbc.update("""
                    insert into ci_order(order_no,request_key,user_id,service_id,service_name,amount,cost_amount,client_platform,status,input_cipher,service_snapshot_cipher,input_summary,query_attempts,expired_at,created_at,updated_at)
                    values(?,?,?,?,?,?,?,?,'pending_payment',?,?,?,?,?,?,?)
                    """,orderNo,requestKey,user.get("id"),service.get("id"),service.get("name"),selectedPrice,service.get("cost_price"),platform,crypto.encrypt(input),crypto.encrypt(service),truncate(summary,100),0,now.plusMinutes(30),now,now);}
            catch(DuplicateKeyException duplicate){List<Map<String,Object>>concurrent=jdbc.queryForList("select * from ci_order where user_id=? and request_key=? limit 1",user.get("id"),requestKey);if(!concurrent.isEmpty())return result(concurrent.getFirst(),null,true);throw duplicate;}
            Long id=jdbc.queryForObject("select id from ci_order where order_no=?",Long.class,orderNo);jdbc.update("insert into ci_payment(order_id,order_no,amount,pay_env,expected_order_type,status,delivery_status,refund_status,created_at,updated_at) values(?,?,?,?,?,\'created\',\'pending\',\'none\',?,?)",id,orderNo,selectedPrice,settingsEnv(clientPayment),"ios".equals(platform)?7:0,now,now);
            return result(jdbc.queryForMap("select * from ci_order where id=?",id),clientPayment,false);
        }catch(BusinessException|IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("订单创建失败",e);}
    }
    public Map<String,Object> status(long userId,String no){Map<String,Object>o=owned(userId,no);if(List.of("pending_payment","payment_review").contains(String.valueOf(o.get("status"))))reconciliation.reconcile(o,userRow(userId));return detail(owned(userId,no));}
    public Map<String,Object> payment(long userId,String no,String requestedPlatform){Map<String,Object>o=owned(userId,no);String platform=payment.normalizePlatform(requestedPlatform);if(requestedPlatform==null||requestedPlatform.isBlank())throw new BusinessException("客户端未提交支付平台，请更新小程序后重试");if(!platform.equals(String.valueOf(o.get("client_platform"))))throw new BusinessException("当前设备平台与订单不一致，请返回服务页重新下单，原订单不会扣款");if(!"pending_payment".equals(o.get("status")))return paymentResult(detail(o),null);Map<String,Object>u=userRow(userId);reconciliation.reconcile(o,u);o=owned(userId,no);if(!"pending_payment".equals(o.get("status")))return paymentResult(detail(o),null);Object exp=o.get("expired_at");if(exp instanceof java.sql.Timestamp t&&t.toLocalDateTime().isBefore(LocalDateTime.now())){jdbc.update("update ci_order set status='cancelled',updated_at=? where id=? and status='pending_payment'",LocalDateTime.now(),o.get("id"));o.put("status","cancelled");return paymentResult(detail(o),null);}Map<String,Object>s=crypto.decrypt(String.valueOf(o.getOrDefault("service_snapshot_cipher","")));if(s.isEmpty())throw new IllegalStateException("订单支付配置快照不可用，请联系客服处理");return paymentResult(safe(o),payment.clientParams(o,u,s));}
    public Map<String,Object> query(long userId,String no){Map<String,Object> o=owned(userId,no);if("paid".equals(o.get("status")))queries.run(o);return detail(owned(userId,no));}
    public Object recoverable(long userId){List<Map<String,Object>> rows=jdbc.queryForList("select * from ci_order where user_id=? and paid_at is not null and status in ('paid','querying','query_failed','success','payment_review') and created_at>=? order by id desc limit 1",userId,LocalDateTime.now().minusHours(24));if(rows.isEmpty())return null;Map<String,Object> d=detail(rows.getFirst());return Boolean.TRUE.equals(d.get("result_expired"))?null:d;}
    /** 记录结果首次查看时间，仅用于运营埋点；结果可见性由「生成后 24 小时」有效期决定，与查看行为无关 */
    public void markResultViewed(long userId,String no){
        Map<String,Object>o=owned(userId,no);
        if(!"success".equals(o.get("status"))||o.get("result_cipher")==null)return;
        if(o.get("result_viewed_at")==null){
            jdbc.update("update ci_order set result_viewed_at=? where id=? and result_viewed_at is null",LocalDateTime.now(),o.get("id"));
        }
    }

    public Map<String,Object> safe(Map<String,Object> o){Map<String,Object> r=new LinkedHashMap<>();for(String k:List.of("order_no","service_name","amount","client_platform","status","paid_at","queried_at","created_at"))r.put(k,o.get(k));return r;}
    public Map<String,Object> detail(Map<String,Object> o){
        Map<String,Object> r=safe(o);
        if("success".equals(o.get("status"))&&o.get("result_cipher")==null){
            // 密文已被清理（历史阅后销毁订单 / 隐私保留期到期），结果无法再展示
            r.put("result_expired",true);
            r.put("result_destroyed",true);
            r.put("message","该查询结果已超过保留期，无法再次查看，如需查看请重新查询。");
        }
        if("success".equals(o.get("status"))&&o.get("result_cipher")!=null){
            // 二级查询（异步/回调）结果长期可查看；一级查询（单次）结果自生成起仅保留 24 小时
            boolean longLived=isLongLived(o);
            LocalDateTime expiresAt=longLived?null:expiryOf(o);
            boolean expired=expiresAt!=null&&!LocalDateTime.now().isBefore(expiresAt);
            if(longLived){
                r.put("result_retention","permanent");
            }else if(expiresAt!=null){
                r.put("result_retention","limited");
                r.put("result_expires_at",expiresAt.format(MINUTE_FORMAT));
                r.put("result_remaining_seconds",Math.max(0,Duration.between(LocalDateTime.now(),expiresAt).getSeconds()));
            }
            if(expired){
                r.put("result_expired",true);
                r.put("result_expired_at",expiresAt.format(MINUTE_FORMAT));
                // 兼容旧版小程序：仍按「结果已销毁」提示，避免旧包展示空白报告
                r.put("result_destroyed",true);
                r.put("message","查询结果自生成起仅保留 24 小时，已于 "+expiresAt.format(MINUTE_FORMAT)+" 到期，如需再次查看请重新查询。");
            }else{
                r.put("result",ResultValueFormatter.formatItems(crypto.decrypt(String.valueOf(o.get("result_cipher"))).getOrDefault("items",List.of())));
            }
            try{delivery(o);}catch(Exception ignored){}
        }
        if(Set.of("query_failed","refunding","refunded").contains(String.valueOf(o.get("status"))) && o.get("result_cipher")==null){
            r.put("message","未查询到信息");
            boolean android="android".equals(String.valueOf(o.get("client_platform")));
            r.put("auto_refund_enabled",android&&"1".equals(settings.value("android_auto_refund_query_failed","0")));
            r.put("apple_refund_url","https://support.apple.com/zh-cn/118223");
            List<Map<String,Object>> payments=jdbc.queryForList("select refund_status,refund_source from ci_payment where order_id=? limit 1",o.get("id"));
            if(!payments.isEmpty()){
                r.put("refund_status",payments.getFirst().getOrDefault("refund_status","none"));
                r.put("refund_source",payments.getFirst().get("refund_source"));
            }
        }
        if("payment_review".equals(o.get("status")))r.put("message","支付信息需要人工核对，请勿重复支付并联系客服");
        // 回调模式：返回 query_id 供前端轮询
        if("querying".equals(String.valueOf(o.get("status")))){
            String providerRequestId=String.valueOf(o.getOrDefault("provider_request_id",""));
            if(!providerRequestId.isBlank()){
                r.put("query_id",providerRequestId);
                // 获取服务的回调超时时间，估算等待时间
                int serviceId=o.get("service_id")instanceof Number n?n.intValue():0;
                if(serviceId>0){
                    List<Map<String,Object>> services=jdbc.queryForList("select callback_timeout_sec from ci_service where id=?",serviceId);
                    if(!services.isEmpty()){
                        int timeoutSec=((Number)services.getFirst().getOrDefault("callback_timeout_sec",120)).intValue();
                        r.put("estimated_wait",timeoutSec<=60?"1":timeoutSec<=180?"1-3":"3-5");
                    }
                }
            }
        }
        return r;
    }
    private void delivery(Map<String,Object>o){delivery.deliver(String.valueOf(o.get("order_no")),false);}
    /** 二级查询（异步/回调）判定：这类结果长期可查看，不受 24 小时有效期约束 */
    private boolean isLongLived(Map<String,Object> o){
        String providerRequestId=String.valueOf(o.getOrDefault("provider_request_id",""));
        if(!providerRequestId.isBlank()&&!"null".equals(providerRequestId))return true;
        Map<String,Object> snapshot=crypto.decrypt(String.valueOf(o.getOrDefault("service_snapshot_cipher","")));
        if(!snapshot.isEmpty())return asyncMode(snapshot);
        // 快照不可解密时退回当前服务配置，避免把二级订单误判成一级而提前过期
        Object serviceId=o.get("service_id");
        if(serviceId instanceof Number id){
            List<Map<String,Object>> rows=jdbc.queryForList("select query_mode,callback_mode from ci_service where id=?",id.longValue());
            if(!rows.isEmpty())return asyncMode(rows.getFirst());
        }
        return false;
    }
    private boolean asyncMode(Map<String,Object> s){
        return "async".equals(String.valueOf(s.getOrDefault("query_mode","single")))||"callback".equals(String.valueOf(s.getOrDefault("callback_mode","none")));
    }
    /** 一级查询结果过期时间：结果生成时间(queried_at) + 24 小时，缺失时退回订单创建时间 */
    private LocalDateTime expiryOf(Map<String,Object> o){
        LocalDateTime generated=firstTime(o.get("queried_at"),o.get("created_at"));
        return generated==null?null:generated.plusHours(RESULT_RETENTION_HOURS);
    }
    private LocalDateTime firstTime(Object... values){
        for(Object v:values){
            if(v instanceof java.sql.Timestamp t)return t.toLocalDateTime();
            if(v instanceof LocalDateTime t)return t;
            if(v instanceof java.time.OffsetDateTime t)return t.toLocalDateTime();
            if(v instanceof java.util.Date d)return LocalDateTime.ofInstant(d.toInstant(),ZoneId.systemDefault());
        }
        return null;
    }
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

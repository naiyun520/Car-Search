package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable, best-effort business email delivery. Mail failures never roll back business state. */
@Service
public class EmailNotificationService {
    public static final String PAYMENT_SUCCESS="payment_success";
    public static final String QUERY_SUCCESS="query_success";
    public static final String QUERY_FAILED="query_failed";
    public static final String REFUND_RESULT="refund_result";
    public static final String PROVIDER_BALANCE="provider_balance";

    private static final Logger log=LoggerFactory.getLogger(EmailNotificationService.class);
    private final JdbcTemplate jdbc;
    private final SettingService settings;
    private final MailService mail;
    private final Executor executor;
    private final AtomicBoolean dispatching=new AtomicBoolean();

    public EmailNotificationService(JdbcTemplate jdbc, SettingService settings, MailService mail,
                                    @Qualifier("notificationExecutor") Executor executor) {
        this.jdbc=jdbc;this.settings=settings;this.mail=mail;this.executor=executor;
    }

    public void paymentSucceeded(Object orderId){event(orderId,PAYMENT_SUCCESS,"支付成功",null,null,null,null);}
    public void querySucceeded(Object orderId){event(orderId,QUERY_SUCCESS,"查询成功",null,null,null,null);}

    public void queryFailed(Object orderId,String providerCode,String requestId,String reason){
        event(orderId,QUERY_FAILED,"查询失败",text(providerCode),requestId,text(reason),null);
    }

    public void refundResult(Object orderId,boolean success,String reason){
        event(orderId,REFUND_RESULT,success?"退款成功":"退款失败",null,null,text(reason),success);
    }

    private void event(Object orderId,String type,String title,String providerCode,String requestId,String reason,Boolean refundSuccess){
        try{
            boolean classifyQueryFailure=QUERY_FAILED.equals(type);
            if(!classifyQueryFailure&&!enabled(type))return;
            List<Map<String,Object>> rows=jdbc.queryForList("""
                    select o.id order_id,o.order_no,o.user_id,o.service_name,o.amount,o.client_platform,o.query_attempts,
                           o.provider_code,o.provider_request_id,o.query_last_error,p.id payment_id,p.wechat_order_type,
                           p.refund_status,p.refund_attempts,p.refund_amount,p.refund_last_error
                    from ci_order o left join ci_payment p on p.order_id=o.id where o.id=? limit 1
                    """,orderId);
            if(rows.isEmpty())return;
            Map<String,Object> row=rows.getFirst();
            if(classifyQueryFailure){
                String storedError=text(row.get("query_last_error"));
                if(storedError.contains("余额不足")&&enabled(PROVIDER_BALANCE)){type=PROVIDER_BALANCE;title="上游接口余额不足";reason=storedError;}
                else if(!enabled(QUERY_FAILED))return;
                else if(!storedError.isBlank())reason=storedError;
            }
            String platform=platform(row);
            String key=eventKey(type,row,providerCode,requestId,reason,refundSuccess);
            String subject="["+platform+"] "+title+" - "+text(row.get("order_no"));
            String body=mail.layout(title,body(row,platform,type,providerCode,requestId,reason,refundSuccess));
            LocalDateTime now=LocalDateTime.now();
            for(String recipient:mail.recipients()) jdbc.update("""
                    insert ignore into ci_email_delivery(event_key,event_type,order_id,recipient,subject,body,status,attempts,next_retry_at,created_at,updated_at)
                    values(?,?,?,?,?,?,'pending',0,?,?,?)
                    """,key,type,row.get("order_id"),recipient,subject,body,now,now,now);
            trigger();
        }catch(Exception error){log.warn("email event enqueue failed type={} order={} message={}",type,orderId,error.getMessage());}
    }

    private String eventKey(String type,Map<String,Object> row,String code,String requestId,String reason,Boolean refundSuccess){
        String payment=text(row.get("payment_id")),order=text(row.get("order_id"));
        return switch(type){
            case PAYMENT_SUCCESS -> "payment_success:"+payment;
            case QUERY_SUCCESS -> "query_success:"+order+":"+integer(row.get("query_attempts"));
            case QUERY_FAILED,PROVIDER_BALANCE -> type+":"+order+":"+integer(row.get("query_attempts"));
            case REFUND_RESULT -> "refund_result:"+payment+":"+(Boolean.TRUE.equals(refundSuccess)?"success":"failed")+":"+integer(row.get("refund_attempts"));
            default -> type+":"+order+":"+Integer.toHexString((text(code)+text(requestId)+text(reason)).hashCode());
        };
    }

    private String body(Map<String,Object> row,String platform,String type,String code,String requestId,String reason,Boolean refundSuccess){
        StringBuilder b=new StringBuilder();
        line(b,"用户平台",platform);line(b,"用户 ID",row.get("user_id"));line(b,"业务订单号",row.get("order_no"));
        line(b,"服务",row.get("service_name"));line(b,"订单金额","¥"+money(row.get("amount")));
        if(QUERY_FAILED.equals(type)||PROVIDER_BALANCE.equals(type)){
            line(b,"供应商业务码",blank(code,text(row.get("provider_code"))));
            line(b,"供应商请求 ID",blank(requestId,text(row.get("provider_request_id"))));
            line(b,"失败原因",blank(reason,text(row.get("query_last_error"))));
            line(b,"查询尝试次数",row.get("query_attempts"));
        }
        if(REFUND_RESULT.equals(type)){
            Object refund=row.get("refund_amount");
            line(b,"退款结果",Boolean.TRUE.equals(refundSuccess)?"成功":"失败");
            line(b,"退款金额","¥"+money(refund==null?row.get("amount"):refund));
            if(!text(reason).isBlank()||!text(row.get("refund_last_error")).isBlank())line(b,"结果说明",blank(reason,text(row.get("refund_last_error"))));
        }
        line(b,"事件时间",LocalDateTime.now());
        return b.toString();
    }

    private void line(StringBuilder b,String name,Object value){b.append("<p><b>").append(h(name)).append("：</b>").append(h(value)).append("</p>");}
    private boolean enabled(String type){return "1".equals(settings.value("email_notify_enabled","0"))&&"1".equals(settings.value(setting(type),"0"));}
    private String setting(String type){return switch(type){case PAYMENT_SUCCESS->"email_event_payment_success";case QUERY_SUCCESS->"email_event_query_success";case QUERY_FAILED->"email_event_query_failed";case REFUND_RESULT->"email_event_refund_result";case PROVIDER_BALANCE->"email_event_provider_balance";default->"";};}
    private String platform(Map<String,Object> row){String p=text(row.get("client_platform")).toLowerCase(Locale.ROOT);if("ios".equals(p))return"iOS";if("android".equals(p))return"安卓";int t=integer(row.get("wechat_order_type"));return t==7||t==8?"iOS":t==0||t==1?"安卓":"未知平台";}

    private void trigger(){try{executor.execute(()->dispatch(50));}catch(Exception ignored){}}
    @Scheduled(fixedDelayString="${car.email-delivery-delay-ms:60000}",initialDelayString="${car.email-delivery-initial-delay-ms:15000}")
    public void retryPending(){trigger();}

    void dispatch(int limit){
        if(!dispatching.compareAndSet(false,true))return;
        try{
            LocalDateTime now=LocalDateTime.now();
            jdbc.update("update ci_email_delivery set status='failed',last_error='发送任务超时，已重新排队',next_retry_at=?,updated_at=? where status='sending' and updated_at<?",now,now,now.minusMinutes(10));
            List<Map<String,Object>> rows=jdbc.queryForList("select id,event_type,recipient,subject,body,attempts from ci_email_delivery where status in ('pending','failed') and next_retry_at<=? order by id limit ?",now,Math.max(1,Math.min(200,limit)));
            for(Map<String,Object> row:rows)send(row);
        }catch(Exception error){log.warn("email delivery dispatch failed: {}",error.getMessage());}
        finally{dispatching.set(false);}
    }

    private void send(Map<String,Object> row){
        Object id=row.get("id");String type=text(row.get("event_type"));LocalDateTime now=LocalDateTime.now();
        if(!enabled(type)){jdbc.update("update ci_email_delivery set status='skipped',last_error='通知总开关或事件开关已关闭',updated_at=? where id=? and status in ('pending','failed')",now,id);return;}
        int claimed=jdbc.update("update ci_email_delivery set status='sending',attempts=attempts+1,updated_at=? where id=? and status in ('pending','failed')",now,id);
        if(claimed==0)return;
        try{
            mail.send(text(row.get("recipient")),text(row.get("subject")),text(row.get("body")));
            jdbc.update("update ci_email_delivery set status='sent',sent_at=?,last_error=null,updated_at=? where id=?",LocalDateTime.now(),LocalDateTime.now(),id);
        }catch(Exception error){
            int attempts=integer(row.get("attempts"))+1;long seconds=Math.min(3600L,60L<<(Math.min(attempts,6)-1));
            jdbc.update("update ci_email_delivery set status='failed',next_retry_at=?,last_error=?,updated_at=? where id=?",LocalDateTime.now().plusSeconds(seconds),cut(error.getMessage(),500),LocalDateTime.now(),id);
            log.warn("email delivery failed id={} recipient={} message={}",id,row.get("recipient"),error.getMessage());
        }
    }

    private String blank(String first,String fallback){return first==null||first.isBlank()?fallback:first;}
    private String money(Object value){try{return new BigDecimal(String.valueOf(value)).setScale(2).toPlainString();}catch(Exception ignored){return"0.00";}}
    private int integer(Object value){try{return value instanceof Number n?n.intValue():Integer.parseInt(text(value));}catch(Exception ignored){return 0;}}
    private String text(Object value){return value==null?"":String.valueOf(value).trim();}
    private String cut(String value,int max){String s=text(value);return s.length()<=max?s:s.substring(0,max);}
    private String h(Object value){return text(value).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}

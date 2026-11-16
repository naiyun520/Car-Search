package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.security.CryptoService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** 将主动获取和直查收敛为一次订单终态提交。 */
@Service
public class QueryCompletionService {
    private final JdbcTemplate jdbc;
    private final CryptoService crypto;
    private final EmailNotificationService email;
    private final PaymentDeliveryService delivery;
    private final TransactionTemplate transaction;

    public QueryCompletionService(JdbcTemplate jdbc,CryptoService crypto,EmailNotificationService email,
                                  PaymentDeliveryService delivery,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.crypto=crypto;this.email=email;this.delivery=delivery;this.transaction=new TransactionTemplate(manager);
    }

    public boolean completeSuccess(Map<String,Object> job,List<Map<String,Object>> items,String providerCode,String requestId){
        LocalDateTime now=LocalDateTime.now();
        AtomicBoolean completed=new AtomicBoolean();
        transaction.executeWithoutResult(tx->{
            Map<String,Object> current=jdbc.queryForMap("select status from ci_order where id=? for update",job.get("order_id"));
            if(!"querying".equals(current.get("status")))return;
            int jobChanged=jdbc.update("update ci_query_job set state='succeeded',provider_request_id=coalesce(provider_request_id,?),last_error_class=null,last_error_code=null,last_error_message=null,next_run_at=null,lock_owner=null,locked_until=null,completed_at=?,updated_at=? where id=? and state not in ('succeeded','failed','no_data','timed_out','submit_unknown','direct_unknown','stopped')",blankToNull(requestId),now,now,job.get("id"));
            if(jobChanged!=1)return;
            int changed=jdbc.update("""
                    update ci_order set status='success',provider_code=?,provider_request_id=?,query_last_error=null,
                      result_cipher=?,queried_at=?,user_visible_until=?,admin_retention_until=?,updated_at=?
                    where id=? and status='querying'
                    """,cut(providerCode,20),blankToNull(requestId),crypto.encrypt(Map.of("items",items)),now,now.plusHours(24),now.plusDays(180),now,job.get("order_id"));
            if(changed==0)return;
            email.querySucceeded(job.get("order_id"));
            completed.set(true);
        });
        if(!completed.get())return false;
        try{String no=jdbc.queryForObject("select order_no from ci_order where id=?",String.class,job.get("order_id"));if(no!=null)delivery.deliver(no,false);}catch(Exception ignored){}
        return true;
    }

    public boolean completeFailure(Map<String,Object> job,String terminal,String providerCode,String requestId,String reason){
        String state=allowedTerminal(terminal)?terminal:"failed";
        LocalDateTime now=LocalDateTime.now();
        AtomicBoolean completed=new AtomicBoolean();
        transaction.executeWithoutResult(tx->{
            Map<String,Object> current=jdbc.queryForMap("select status from ci_order where id=? for update",job.get("order_id"));
            if(!"querying".equals(current.get("status")))return;
            Integer active=jdbc.queryForObject("select count(*) from ci_query_job where id=? and state not in ('succeeded','failed','no_data','timed_out','submit_unknown','direct_unknown','stopped')",Integer.class,job.get("id"));
            if(active==null||active!=1)return;
            int changed=jdbc.update("update ci_order set status='query_failed',provider_code=?,provider_request_id=coalesce(?,provider_request_id),query_last_error=?,queried_at=?,updated_at=? where id=? and status='querying'",cut(providerCode,20),blankToNull(requestId),cut(reason,500),now,now,job.get("order_id"));
            if(changed==0)return;
            jdbc.update("update ci_query_job set state=?,provider_request_id=coalesce(provider_request_id,?),last_error_class=?,last_error_code=?,last_error_message=?,next_run_at=null,lock_owner=null,locked_until=null,completed_at=?,updated_at=? where id=?",state,blankToNull(requestId),errorClass(state),cut(providerCode,80),cut(reason,500),now,now,job.get("id"));
            email.queryFailed(job.get("order_id"),providerCode,requestId,reason);
            completed.set(true);
        });
        return completed.get();
    }

    private boolean allowedTerminal(String state){return List.of("failed","no_data","timed_out","submit_unknown","direct_unknown","stopped").contains(state);}
    private String errorClass(String state){return switch(state){case"timed_out"->"timeout";case"submit_unknown","direct_unknown"->"unknown";case"stopped"->"manual";default->"permanent";};}
    private String blankToNull(String value){return value==null||value.isBlank()?null:value;}
    private String cut(String value,int max){String v=value==null?"":value.replaceAll("[\\r\\n]+"," ").trim();return v.length()<=max?v:v.substring(0,max);}
}

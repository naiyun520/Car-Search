package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MaintenanceScheduler {
    private static final Logger log=LoggerFactory.getLogger(MaintenanceScheduler.class);
    private final JdbcTemplate jdbc;private final PaymentLifecycleService lifecycle;private final AndroidAutoRefundService autoRefund;private final SettingService settings;private final EmailNotificationService email;private final AtomicBoolean reconciling=new AtomicBoolean();private final AtomicBoolean failingQueries=new AtomicBoolean();
    public MaintenanceScheduler(JdbcTemplate jdbc,PaymentLifecycleService lifecycle,AndroidAutoRefundService autoRefund,SettingService settings,EmailNotificationService email){this.jdbc=jdbc;this.lifecycle=lifecycle;this.autoRefund=autoRefund;this.settings=settings;this.email=email;}
    @Scheduled(fixedDelayString="${car.reconcile-delay-ms:60000}",initialDelayString="${car.reconcile-initial-delay-ms:30000}")public void reconcile(){if(!reconciling.compareAndSet(false,true))return;try{Map<String,Integer>result=new java.util.LinkedHashMap<>(lifecycle.reconcilePending(50));result.put("android_auto_refunds",autoRefund.processPending(50));log.debug("payment reconciliation result={}",result);}catch(Exception e){log.error("payment reconciliation failed",e);}finally{reconciling.set(false);}}
    @Scheduled(cron="${car.cleanup-cron:0 17 * * * *}",zone="Asia/Shanghai")public void cleanup(){LocalDateTime now=LocalDateTime.now();jdbc.update("delete from ci_user_token where expires_at<?",now);jdbc.update("delete from ci_admin_token where expires_at<?",now);jdbc.update("delete from ci_admin_login_attempt where updated_at<?",now.minusDays(1));int days=Math.max(1,Math.min(365,integer(settings.value("privacy_retention_days","30"),30)));jdbc.update("update ci_order set input_cipher='',input_summary='',result_cipher=null,updated_at=? where created_at<? and status<>'querying' and (input_cipher<>'' or input_summary<>'' or result_cipher is not null)",now,now.minusDays(days));}

    /**
     * 回调-only 超时兜底：callback_timeout_sec（+60s 宽限）内未收到供应商回调的查询中订单，
     * 判为 query_failed 并邮件通知。**不调用任何供应商接口**——获取报告按次计费，
     * 绝不能为兜底再去取报告（成本只允许发生在下单那一次）。
     * 判失败后 Android 自动退款（若开启）/iOS 用户自行申请 照常接管。
     */
    @Scheduled(fixedDelayString="${car.query-timeout-delay-ms:60000}",initialDelayString="${car.query-timeout-initial-delay-ms:45000}")
    public void failStaleQueries(){
        if(!failingQueries.compareAndSet(false,true))return;
        try{
            List<Map<String,Object>> stale=jdbc.queryForList("""
                    select o.id order_id,o.provider_request_id,coalesce(s.callback_timeout_sec,120) timeout_sec
                    from ci_order o join ci_service s on s.id=o.service_id
                    where o.status='querying' and o.paid_at is not null and o.provider_request_id is not null
                      and coalesce(s.callback_timeout_sec,120) > 0
                      and coalesce(o.updated_at,o.created_at) < date_sub(now(), interval coalesce(s.callback_timeout_sec,120)+60 second)
                    limit 50
                    """);
            for(Map<String,Object> row:stale){
                long orderId=((Number)row.get("order_id")).longValue();
                String queryId=String.valueOf(row.get("provider_request_id"));
                long timeoutSec=((Number)row.get("timeout_sec")).longValue();
                String reason="回调超时：超过 "+timeoutSec+" 秒未收到供应商查询结果";
                LocalDateTime now=LocalDateTime.now();
                int updated=jdbc.update("update ci_order set status='query_failed',query_last_error=?,queried_at=?,updated_at=? where id=? and status='querying'",reason,now,now,orderId);
                if(updated==0)continue;
                jdbc.update("update ci_query set status='timeout',result_code='timeout',result_message=?,completed_at=?,updated_at=? where query_id=? and status in ('pending','processing')",reason,now,now,queryId);
                log.warn("[QueryTimeout] 回调超时判失败 orderId={}, queryId={}, timeoutSec={}",orderId,queryId,timeoutSec);
                try{email.queryFailed(orderId,"timeout",queryId,reason);}catch(Exception ignored){}
            }
        }catch(Exception e){
            log.error("query timeout sweep failed",e);
        }finally{
            failingQueries.set(false);
        }
    }
    private int integer(String v,int d){try{return Integer.parseInt(v);}catch(Exception e){return d;}}
}

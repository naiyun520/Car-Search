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
    @Scheduled(scheduler="paymentTaskScheduler",fixedDelayString="${car.reconcile-delay-ms:60000}",initialDelayString="${car.reconcile-initial-delay-ms:30000}")public void reconcile(){if(!reconciling.compareAndSet(false,true))return;try{Map<String,Integer>result=new java.util.LinkedHashMap<>(lifecycle.reconcilePending(50));result.put("android_auto_refunds",autoRefund.processPending(50));log.debug("payment reconciliation result={}",result);}catch(Exception e){log.error("payment reconciliation failed",e);}finally{reconciling.set(false);}}
    @Scheduled(cron="${car.cleanup-cron:0 17 * * * *}",zone="Asia/Shanghai")public void cleanup(){LocalDateTime now=LocalDateTime.now();jdbc.update("delete from ci_user_token where expires_at<?",now);jdbc.update("delete from ci_admin_token where expires_at<?",now);jdbc.update("delete from ci_admin_login_attempt where updated_at<?",now.minusDays(1));int days=Math.max(1,Math.min(365,integer(settings.value("privacy_retention_days","180"),180)));LocalDateTime cutoff=now.minusDays(days);jdbc.update("update ci_order set input_cipher='',input_summary='',result_cipher=null,updated_at=? where status<>'querying' and ((admin_retention_until is not null and admin_retention_until<?) or (admin_retention_until is null and created_at<?)) and (input_cipher<>'' or input_summary<>'' or result_cipher is not null)",now,now,cutoff);jdbc.update("update ci_query q join ci_order o on o.provider_request_id=q.query_id set q.request_body='',q.result_json='{}',q.result_message='',q.updated_at=? where o.status<>'querying' and coalesce(o.admin_retention_until,o.created_at)<?",now,cutoff);jdbc.update("update ci_order o join ci_order_result_revision r on r.id=o.active_result_revision_id set o.active_result_revision_id=null,o.updated_at=? where r.created_at<?",now,cutoff);jdbc.update("delete from ci_order_result_revision where created_at<?",cutoff);}

    private int integer(String v,int d){try{return Integer.parseInt(v);}catch(Exception e){return d;}}
}

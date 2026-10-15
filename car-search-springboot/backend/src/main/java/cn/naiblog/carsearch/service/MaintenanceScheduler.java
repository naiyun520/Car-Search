package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MaintenanceScheduler {
    private static final Logger log=LoggerFactory.getLogger(MaintenanceScheduler.class);
    private final JdbcTemplate jdbc;private final PaymentLifecycleService lifecycle;private final AndroidAutoRefundService autoRefund;private final SettingService settings;private final AtomicBoolean reconciling=new AtomicBoolean();
    public MaintenanceScheduler(JdbcTemplate jdbc,PaymentLifecycleService lifecycle,AndroidAutoRefundService autoRefund,SettingService settings){this.jdbc=jdbc;this.lifecycle=lifecycle;this.autoRefund=autoRefund;this.settings=settings;}
    @Scheduled(fixedDelayString="${car.reconcile-delay-ms:60000}",initialDelayString="${car.reconcile-initial-delay-ms:30000}")public void reconcile(){if(!reconciling.compareAndSet(false,true))return;try{Map<String,Integer>result=new java.util.LinkedHashMap<>(lifecycle.reconcilePending(50));result.put("android_auto_refunds",autoRefund.processPending(50));log.debug("payment reconciliation result={}",result);}catch(Exception e){log.error("payment reconciliation failed",e);}finally{reconciling.set(false);}}
    @Scheduled(cron="${car.cleanup-cron:0 17 * * * *}",zone="Asia/Shanghai")public void cleanup(){LocalDateTime now=LocalDateTime.now();jdbc.update("delete from ci_user_token where expires_at<?",now);jdbc.update("delete from ci_admin_token where expires_at<?",now);jdbc.update("delete from ci_admin_login_attempt where updated_at<?",now.minusDays(1));int days=Math.max(1,Math.min(365,integer(settings.value("privacy_retention_days","30"),30)));jdbc.update("update ci_order set input_cipher='',input_summary='',result_cipher=null,updated_at=? where created_at<? and status<>'querying' and (input_cipher<>'' or input_summary<>'' or result_cipher is not null)",now,now.minusDays(days));}
    private int integer(String v,int d){try{return Integer.parseInt(v);}catch(Exception e){return d;}}
}

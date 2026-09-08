package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MaintenanceScheduler {
    private static final Logger log=LoggerFactory.getLogger(MaintenanceScheduler.class);
    private final JdbcTemplate jdbc;private final PaymentLifecycleService lifecycle;private final SettingService settings;private final MailService mail;private final AtomicBoolean reconciling=new AtomicBoolean();
    public MaintenanceScheduler(JdbcTemplate jdbc,PaymentLifecycleService lifecycle,SettingService settings,MailService mail){this.jdbc=jdbc;this.lifecycle=lifecycle;this.settings=settings;this.mail=mail;}
    @Scheduled(fixedDelayString="${car.reconcile-delay-ms:60000}",initialDelayString="${car.reconcile-initial-delay-ms:30000}")public void reconcile(){if(!reconciling.compareAndSet(false,true))return;try{log.debug("payment reconciliation result={}",lifecycle.reconcilePending(50));}catch(Exception e){log.error("payment reconciliation failed",e);}finally{reconciling.set(false);}}
    @Scheduled(cron="${car.cleanup-cron:0 17 * * * *}",zone="Asia/Shanghai")public void cleanup(){LocalDateTime now=LocalDateTime.now();jdbc.update("delete from ci_user_token where expires_at<?",now);jdbc.update("delete from ci_admin_token where expires_at<?",now);jdbc.update("delete from ci_admin_login_attempt where updated_at<?",now.minusDays(1));int days=Math.max(1,Math.min(365,integer(settings.value("privacy_retention_days","30"),30)));jdbc.update("update ci_order set input_cipher='',input_summary='',result_cipher=null,updated_at=? where created_at<? and status<>'querying' and (input_cipher<>'' or input_summary<>'' or result_cipher is not null)",now,now.minusDays(days));}
    @Scheduled(cron="${car.daily-email-cron:0 0 8 * * *}",zone="Asia/Shanghai")public void dailyEmail(){if(!"1".equals(settings.value("email_notify_daily","0"))||!mail.available())return;LocalDateTime day=LocalDate.now().atStartOfDay(),month=LocalDate.now().withDayOfMonth(1).atStartOfDay();long users=scalar("select count(*) from ci_user"),newUsers=scalar("select count(*) from ci_user where created_at>=?",day),orders=scalar("select count(*) from ci_order where created_at>=?",day);BigDecimal today=decimal("select coalesce(sum(amount),0) from ci_order where paid_at>=?",day),refund=decimal("select coalesce(sum(refund_amount),0) from ci_payment where refunded_at>=?",day).add(decimal("select coalesce(sum(manual_refund_amount),0) from ci_order where manual_refund_time is not null and paid_at>=?",day)),cost=decimal("select coalesce(sum(cost_amount),0) from ci_order where queried_at>=?",day),monthNet=decimal("select coalesce(sum(amount),0) from ci_order where paid_at>=?",month);String body="<p>累计用户："+users+"，今日新增："+newUsers+"</p><p>今日订单："+orders+"，今日实收：¥"+today+"，退款：¥"+refund+"，成本：¥"+cost+"，利润：¥"+today.subtract(refund).subtract(cost)+"</p><p>本月累计实收：¥"+monthNet+"</p>";mail.notifyAll("每日运营简报 - "+LocalDate.now(),mail.layout("每日运营数据简报",body));}
    private int integer(String v,int d){try{return Integer.parseInt(v);}catch(Exception e){return d;}}private long scalar(String s,Object...a){Long v=jdbc.queryForObject(s,Long.class,a);return v==null?0:v;}private BigDecimal decimal(String s,Object...a){BigDecimal v=jdbc.queryForObject(s,BigDecimal.class,a);return v==null?BigDecimal.ZERO:v;}
}

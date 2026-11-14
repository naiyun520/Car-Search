package cn.naiblog.carsearch.config;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 切换前必须结清尚在原执行器中的查询，绝不通过重下单迁移。 */
@Component @Order(2147483646)
public class UnifiedFulfillmentVerifier implements ApplicationRunner {
 private final JdbcTemplate jdbc;
 public UnifiedFulfillmentVerifier(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void run(ApplicationArguments args){
   Long callbackJobs=jdbc.queryForObject("select count(*) from ci_query_job where job_type='callback' and state not in ('succeeded','failed','no_data','timed_out','submit_unknown','direct_unknown','stopped')",Long.class);
   if(callbackJobs!=null&&callbackJobs>0)throw new IllegalStateException("主动获取切换检查失败：仍有等待供应商回调的任务。请先核对并结束这些订单，禁止重新下单迁移。");
   Long orphan=jdbc.queryForObject("select count(*) from ci_order o where o.status='querying' and not exists(select 1 from ci_query_job j where j.order_id=o.id)",Long.class);
   if(orphan!=null&&orphan>0)throw new IllegalStateException("统一任务切换检查失败：仍有 "+orphan+" 笔查询中订单没有任务。请暂停下单，先完成这些查询并核对供应商结果；禁止重下单迁移。");
 }
}

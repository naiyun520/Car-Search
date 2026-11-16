package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.security.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** 所有查询的唯一执行器；总开关仅暂停调度，不改变订单的执行方式。 */
@Component
public class QueryJobScheduler {
    private static final Logger log=LoggerFactory.getLogger(QueryJobScheduler.class);
    private final JdbcTemplate jdbc;
    private final CryptoService crypto;
    private final QueryJobService jobs;
    private final ProviderService provider;
    private final ProviderCallService calls;
    private final ProviderBalanceService balances;
    private final QueryCompletionService completion;
    private final String workerId="query-"+UUID.randomUUID();
    private final AtomicBoolean running=new AtomicBoolean();
    private final Executor executor;
    private final Semaphore slots;
    private volatile boolean stopping;

    @jakarta.annotation.PreDestroy
    public void stopDraining(){stopping=true;}

    public QueryJobScheduler(JdbcTemplate jdbc,CryptoService crypto,QueryJobService jobs,ProviderService provider,
                             ProviderCallService calls,QueryCompletionService completion){
        this(jdbc,crypto,jobs,provider,calls,completion,null,Runnable::run,1);
    }

    QueryJobScheduler(JdbcTemplate jdbc,CryptoService crypto,QueryJobService jobs,ProviderService provider,
                      ProviderCallService calls,QueryCompletionService completion,ProviderBalanceService balances){
        this(jdbc,crypto,jobs,provider,calls,completion,balances,Runnable::run,1);
    }

    @Autowired
    public QueryJobScheduler(JdbcTemplate jdbc,CryptoService crypto,QueryJobService jobs,ProviderService provider,
                             ProviderCallService calls,QueryCompletionService completion,ProviderBalanceService balances,
                             @Qualifier("queryWorkerExecutor") ThreadPoolTaskExecutor workers){
        this(jdbc,crypto,jobs,provider,calls,completion,balances,workers,workers.getMaxPoolSize());
    }

    QueryJobScheduler(JdbcTemplate jdbc,CryptoService crypto,QueryJobService jobs,ProviderService provider,
                      ProviderCallService calls,QueryCompletionService completion,Executor executor,int concurrency){
        this(jdbc,crypto,jobs,provider,calls,completion,null,executor,concurrency);
    }

    QueryJobScheduler(JdbcTemplate jdbc,CryptoService crypto,QueryJobService jobs,ProviderService provider,
                      ProviderCallService calls,QueryCompletionService completion,ProviderBalanceService balances,
                      Executor executor,int concurrency){
        this.jdbc=jdbc;this.crypto=crypto;this.jobs=jobs;this.provider=provider;this.calls=calls;
        this.completion=completion;this.balances=balances;this.executor=executor;this.slots=new Semaphore(Math.max(1,concurrency));
    }

    @Scheduled(scheduler="queryTaskScheduler",fixedDelayString="${car.query-job-delay-ms:10000}",initialDelayString="${car.query-job-initial-delay-ms:20000}")
    public void runDueJobs(){
        if(stopping||!jobs.globallyEnabled()||!running.compareAndSet(false,true))return;
        try{
            jobs.recoverFetchLocks();
            recoverUnsafeLocks();
            recoverPaidOrders();
            for(int i=0;i<20&&!stopping;i++){
                if(!slots.tryAcquire())break;
                Map<String,Object> claimedJob=null;
                AtomicBoolean began=new AtomicBoolean();
                try{
                    List<Map<String,Object>> claimed=jobs.claimDueJobs(1,workerId);
                    if(claimed.isEmpty()){slots.release();break;}
                    claimedJob=claimed.getFirst();
                    Map<String,Object> job=claimedJob;
                    executor.execute(()->{began.set(true);drain(job);});
                }catch(RuntimeException rejected){
                    if(!began.get()){
                        slots.release();
                        if(claimedJob!=null)jobs.releaseUnstartedClaim(claimedJob);
                    }
                    log.warn("query dispatch deferred",rejected);
                    break;
                }
            }
        }catch(Exception error){log.error("query job scheduler failed",error);}
        finally{running.set(false);}
    }

    /** 一个容量许可对应一个工作线程；完成后继续从数据库领取，不等待下一次定时扫描。 */
    private void drain(Map<String,Object> first){
        try{
            Map<String,Object> current=first;
            for(int processed=0;processed<50;processed++){
                process(current);
                if(stopping||Thread.currentThread().isInterrupted()||!jobs.globallyEnabled()||processed==49)return;
                List<Map<String,Object>> next=jobs.claimDueJobs(1,workerId);
                if(next.isEmpty())return;
                current=next.getFirst();
            }
        }finally{slots.release();}
    }

    private void recoverPaidOrders(){
        for(Map<String,Object> order:jdbc.queryForList("select o.* from ci_order o where o.status='paid' and not exists(select 1 from ci_query_job j where j.order_id=o.id) order by o.id limit 50")){
            try{jobs.enqueuePaidOrder(((Number)order.get("id")).longValue());}
            catch(Exception e){log.warn("paid order enqueue deferred order={}",order.get("id"));}
        }
    }

    void process(Map<String,Object> job){
        try{
            Integer owned=jdbc.queryForObject("select count(*) from ci_query_job where id=? and state=? and lock_owner=? and version=? and locked_until>?",Integer.class,job.get("id"),job.get("state"),job.get("lock_owner"),job.get("version"),LocalDateTime.now());
            if(owned==null||owned!=1)return;
            Map<String,Object> order=jdbc.queryForMap("select * from ci_order where id=?",job.get("order_id"));
            if(!"querying".equals(text(order.get("status")))){
                jobs.stop(number(job.get("order_id")),"订单已不处于查询中，停止供应商任务");
                return;
            }
            Map<String,Object> service=jobs.serviceForOrder(order);
            switch(text(job.get("state"))){
                case"running_direct"->direct(job,order,service);
                case"submitting"->submit(job,order,service);
                case"fetching"->fetch(job,service);
                default->log.warn("unexpected claimed query state={} job={}",job.get("state"),job.get("id"));
            }
        }catch(Exception error){
            log.error("query job processing failed job={} state={} reason={}",job.get("id"),job.get("state"),safe(error.getMessage()));
            completion.completeFailure(job,"failed","error",text(job.get("provider_request_id")),"任务执行异常："+safe(error.getMessage()));
        }
    }

    private void direct(Map<String,Object> job,Map<String,Object> order,Map<String,Object> service){
        LocalDateTime started=LocalDateTime.now();
        BigDecimal cost=money(order.get("cost_amount"));
        long callId=calls.start(job,"DIRECT_QUERY","unknown",cost);
        captureBalance(job,callId,"before");
        try{
            ProviderService.Result result=provider.executeDirect(service,crypto.decrypt(text(order.get("input_cipher"))));
            calls.succeed(callId,"confirmed",result.requestId(),result.code(),started);
            completion.completeSuccess(job,result.data(),result.code(),result.requestId());
        }catch(ProviderQueryException rejected){
            calls.fail(callId,"failed","not_billable",rejected.requestId(),rejected.providerCode(),"permanent",rejected.getMessage(),started);
            completion.completeFailure(job,"failed",rejected.providerCode(),rejected.requestId(),rejected.getMessage());
        }catch(Exception unknown){
            calls.fail(callId,"unknown","unknown","","","unknown",unknown.getMessage(),started);
            completion.completeFailure(job,"direct_unknown","error","","同步查询结果不确定，已停止且不会重复查询");
        }finally{
            captureBalance(job,callId,"after");
        }
    }

    private void submit(Map<String,Object> job,Map<String,Object> order,Map<String,Object> service){
        LocalDateTime started=LocalDateTime.now();
        jdbc.update("update ci_query_job set submit_attempts=submit_attempts+1 where id=? and state='submitting'",job.get("id"));
        long callId=calls.start(job,"SUBMIT","confirmed",money(order.get("cost_amount")));
        captureBalance(job,callId,"before");
        try{
            ProviderService.SubmitResult result=provider.submitAsync(service,crypto.decrypt(text(order.get("input_cipher"))));
            calls.succeed(callId,"confirmed",result.requestId(),result.businessCode(),started);
            int timeout=Math.max(0,number(service.get("poll_timeout_sec"),0));
            jobs.submitted(job,result.requestId(),timeout);
        }catch(ProviderQueryException rejected){
            calls.fail(callId,"failed","confirmed",rejected.requestId(),rejected.providerCode(),"permanent",rejected.getMessage(),started);
            completion.completeFailure(job,"failed",rejected.providerCode(),rejected.requestId(),rejected.getMessage());
        }catch(Exception unknown){
            calls.fail(callId,"unknown","confirmed","","","unknown",unknown.getMessage(),started);
            completion.completeFailure(job,"submit_unknown","error","","异步下单结果不确定，已停止且不会重复下单");
        }finally{
            captureBalance(job,callId,"after");
        }
    }

    private void fetch(Map<String,Object> job,Map<String,Object> service){
        LocalDateTime deadline=date(job.get("deadline_at"));
        if((deadline!=null&&!LocalDateTime.now().isBefore(deadline))||number(job.get("fetch_attempts"))>=Math.max(1,number(job.get("max_fetch_attempts"),20))){
            completion.completeFailure(job,"timed_out","timeout",text(job.get("provider_request_id")),"已达到获取报告的截止时间或次数上限");return;
        }
        jdbc.update("update ci_query_job set fetch_attempts=fetch_attempts+1 where id=? and state='fetching'",job.get("id"));
        LocalDateTime started=LocalDateTime.now();
        long callId=calls.start(job,"FETCH","not_billable",BigDecimal.ZERO);
        int nextAttempts=number(job.get("fetch_attempts"))+1;
        try{
            ProviderService.FetchResult result=provider.fetchReport(service,text(job.get("provider_request_id")));
            calls.succeed(callId,"not_billable",result.requestId(),result.businessCode(),started);
            switch(result.state()){
                case SUCCESS->completion.completeSuccess(job,result.data(),result.businessCode(),result.requestId());
                case FAILED->completion.completeFailure(job,"failed",result.businessCode(),result.requestId(),result.message());
                case NO_DATA->completion.completeFailure(job,"no_data",result.businessCode(),result.requestId(),result.message());
                case RUNNING->rescheduleOrTimeout(job,nextAttempts,"供应商仍在处理中");
            }
        }catch(Exception error){
            calls.fail(callId,"failed","not_billable",text(job.get("provider_request_id")),providerCode(error),permanent(error)?"permanent":"transient",error.getMessage(),started);
            if(permanent(error))completion.completeFailure(job,"failed",providerCode(error),text(job.get("provider_request_id")),safe(error.getMessage()));
            else rescheduleOrTimeout(job,nextAttempts,safe(error.getMessage()));
        }
    }

    private void rescheduleOrTimeout(Map<String,Object> job,int attempts,String reason){
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime deadline=date(job.get("deadline_at"));
        int max=Math.max(1,number(job.get("max_fetch_attempts"),20));
        if(attempts>=max||(deadline!=null&&!now.isBefore(deadline))){
            completion.completeFailure(job,"timed_out","timeout",text(job.get("provider_request_id")),"已达到获取报告的时间或次数上限（本次累计 "+attempts+" 次）");
            return;
        }
        int interval=Math.max(60,number(job.get("interval_seconds"),60));
        jdbc.update("update ci_query_job set state='waiting_result',fetch_attempts=?,next_run_at=?,lock_owner=null,locked_until=null,last_error_class='transient',last_error_message=?,updated_at=? where id=? and state='fetching'",attempts,now.plusSeconds(interval),safe(reason),now,job.get("id"));
    }

    private void recoverUnsafeLocks(){
        for(Map<String,Object> job:jobs.expiredUnsafeLocks(20)){
            boolean submit="submitting".equals(text(job.get("state")));
            String terminal=submit?"submit_unknown":"direct_unknown";
            calls.expireStarted(job.get("id"),submit?"confirmed":"unknown","unknown","进程中断或调用锁超时");
            completion.completeFailure(job,terminal,"timeout",text(job.get("provider_request_id")),submit?"异步下单过程被中断，已停止且不会重复下单":"同步查询过程被中断，已停止且不会重复查询");
        }
    }

    private boolean permanent(Exception error){String m=safe(error.getMessage());return m.contains("未配置")||m.contains("已禁用")||m.contains("无效")||m.contains("不安全")||error instanceof ProviderQueryException;}
    private void captureBalance(Map<String,Object> job,long callId,String phase){
        if(balances==null)return;
        try{
            int providerId=number(job.get("provider_id"));
            if(providerId>0)balances.captureOrderBalance(longNumber(job.get("order_id")),callId,providerId,phase);
        }catch(Exception error){
            log.warn("order balance capture failed order={} call={} phase={} reason={}",job.get("order_id"),callId,phase,safe(error.getMessage()));
        }
    }
    private String providerCode(Exception error){return error instanceof ProviderQueryException p?p.providerCode():"error";}
    private long longNumber(Object value){try{return value instanceof Number n?n.longValue():Long.parseLong(text(value));}catch(Exception ignored){return 0;}}
    private LocalDateTime date(Object value){return value instanceof java.sql.Timestamp t?t.toLocalDateTime():value instanceof LocalDateTime d?d:null;}
    private int number(Object value){return number(value,0);}
    private int number(Object value,int fallback){try{return value instanceof Number n?n.intValue():Integer.parseInt(text(value));}catch(Exception ignored){return fallback;}}
    private BigDecimal money(Object value){try{return new BigDecimal(String.valueOf(value));}catch(Exception ignored){return BigDecimal.ZERO;}}
    private String text(Object value){return value==null?"":String.valueOf(value).trim();}
    private String safe(String value){String v=value==null?"unknown":value.replaceAll("[\\r\\n]+"," ");return v.length()<=300?v:v.substring(0,300);}
}

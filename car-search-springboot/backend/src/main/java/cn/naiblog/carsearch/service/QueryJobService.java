package cn.naiblog.carsearch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 持久化查询任务的创建、抢占和安全锁恢复。 */
@Service
public class QueryJobService {
    private final JdbcTemplate jdbc;
    private final boolean globallyEnabled;
    private final QueryCompletionService completion;
    private final cn.naiblog.carsearch.security.CryptoService crypto;

    public QueryJobService(JdbcTemplate jdbc,cn.naiblog.carsearch.security.CryptoService crypto,QueryCompletionService completion,@Value("${car.query-jobs-enabled:true}")boolean globallyEnabled){
        this.jdbc=jdbc;this.crypto=crypto;this.completion=completion;this.globallyEnabled=globallyEnabled;
    }

    public boolean globallyEnabled(){return globallyEnabled;}

    @Transactional
    public Map<String,Object> enqueuePaidOrder(long orderId){
        Map<String,Object> order=jdbc.queryForMap("select * from ci_order where id=? for update",orderId);
        String orderStatus=text(order.get("status"));
        if(!"paid".equals(orderStatus)&&!"querying".equals(orderStatus))return Map.of("status",orderStatus);
        List<Map<String,Object>> existing=jdbc.queryForList("select id,state from ci_query_job where order_id=?",orderId);
        if(!existing.isEmpty())return Map.of("status",orderStatus,"state",existing.getFirst().get("state"));
        Map<String,Object> service=serviceForOrder(order);
        if("querying".equals(orderStatus))return Map.of("status","querying");
        boolean async="async".equals(text(service.get("query_mode")));
        String type=async?"async":"direct";
        String state=async?"pending_submit":"pending_direct";
        int interval=Math.max(60,number(service.get("poll_interval_sec"),60));
        int maxAttempts=Math.max(1,number(service.get("poll_max_attempts"),20));
        Integer providerId=number(service.get("provider_api_id"))>0?number(service.get("provider_api_id")):null;
        LocalDateTime now=LocalDateTime.now();
        try{
            jdbc.update("""
                    insert into ci_query_job(order_id,service_id,provider_id,job_type,state,submit_attempts,fetch_attempts,max_fetch_attempts,interval_seconds,next_run_at,deadline_at,version,created_at,updated_at)
                    values(?,?,?,?,?,0,0,?,?,?,?,0,?,?)
                    """,orderId,service.get("id"),providerId,type,state,maxAttempts,interval,now,async?null:now.plusSeconds(90),now,now);
        }catch(DuplicateKeyException ignored){/* 并发入队由 order_id 唯一键收敛 */}
        jdbc.update("update ci_order set status='querying',query_attempts=query_attempts+1,fulfillment_version=2,updated_at=? where id=? and status='paid'",now,orderId);
        return jdbc.queryForMap("select id,state,next_run_at,deadline_at from ci_query_job where order_id=?",orderId);
    }

    public Map<String,Object> serviceForOrder(Map<String,Object> order){
        String snapshot=text(order.get("service_snapshot_cipher"));
        if(snapshot.isBlank())return jdbc.queryForMap("select * from ci_service where id=?",order.get("service_id"));
        Map<String,Object> service=crypto.decrypt(snapshot);
        if(service.isEmpty())throw new IllegalStateException("订单服务快照无法解密，已停止调用供应商");
        return service;
    }

    @Transactional
    public boolean submitted(Map<String,Object> job,String requestId,int timeout){
        LocalDateTime now=LocalDateTime.now();
        Map<String,Object> order=jdbc.queryForMap("select status from ci_order where id=? for update",job.get("order_id"));
        if(!"querying".equals(order.get("status")))return false;
        int changed=jdbc.update("update ci_query_job set state=?,provider_request_id=?,next_run_at=?,deadline_at=?,lock_owner=null,locked_until=null,updated_at=? where id=? and state='submitting' and lock_owner=? and version=?",
            "waiting_result",requestId,now.plusSeconds(Math.max(60,number(job.get("interval_seconds"),60))),timeout>0?now.plusSeconds(timeout):null,now,job.get("id"),job.get("lock_owner"),job.get("version"));
        if(changed!=1)return false;
        jdbc.update("update ci_order set provider_request_id=?,provider_code='processing',updated_at=? where id=? and status='querying'",requestId,now,job.get("order_id"));
        return true;
    }

    public List<Map<String,Object>> claimDueJobs(int requestedLimit,String workerId){
        int limit=Math.max(1,Math.min(100,requestedLimit));
        List<Map<String,Object>> claimed=new ArrayList<>();
        // 多个工作线程可能同时读到同一候选行；CAS失败只表示被别人领取，不表示数据库已无任务。
        for(int retry=0;retry<8&&claimed.size()<limit;retry++){
            LocalDateTime now=LocalDateTime.now();
            List<Map<String,Object>> candidates=jdbc.queryForList("""
                    select id,state from ci_query_job
                    where state in ('pending_direct','pending_submit','waiting_result')
                      and next_run_at is not null and next_run_at<=?
                      and (locked_until is null or locked_until<?)
                    order by next_run_at,id limit ?
                    """,now,now,limit-claimed.size());
            if(candidates.isEmpty())break;
            for(Map<String,Object> row:candidates){
                String from=text(row.get("state"));
                String to=switch(from){case"pending_direct"->"running_direct";case"pending_submit"->"submitting";default->"fetching";};
                int changed=jdbc.update("update ci_query_job set state=?,lock_owner=?,locked_until=?,version=version+1,updated_at=? where id=? and state=? and (locked_until is null or locked_until<?)",to,workerId,now.plusSeconds(90),now,row.get("id"),from,now);
                if(changed==1)claimed.add(jdbc.queryForMap("select * from ci_query_job where id=?",row.get("id")));
            }
        }
        return claimed;
    }

    /** 线程池拒绝尚未执行的任务时归还领取，绝不重新提交已经开始的供应商请求。 */
    public void releaseUnstartedClaim(Map<String,Object> job){
        String prior=switch(text(job.get("state"))){
            case "running_direct"->"pending_direct";case "submitting"->"pending_submit";default->"waiting_result";
        };
        jdbc.update("update ci_query_job set state=?,lock_owner=null,locked_until=null,version=version+1,updated_at=? where id=? and state=? and lock_owner=? and version=?",
            prior,LocalDateTime.now(),job.get("id"),job.get("state"),job.get("lock_owner"),job.get("version"));
    }

    public int recoverFetchLocks(){
        LocalDateTime now=LocalDateTime.now();
        return jdbc.update("update ci_query_job set state='waiting_result',lock_owner=null,locked_until=null,next_run_at=?,version=version+1,last_error_class='transient',last_error_message='获取报告任务锁超时，已安全恢复',updated_at=? where state='fetching' and locked_until<?",now,now,now);
    }

    public List<Map<String,Object>> expiredUnsafeLocks(int limit){
        return jdbc.queryForList("select * from ci_query_job where state in ('submitting','running_direct') and locked_until<? order by locked_until limit ?",LocalDateTime.now(),Math.max(1,Math.min(100,limit)));
    }

    @Transactional
    public void stop(long orderId,String reason){
        for(Map<String,Object> job:jdbc.queryForList("select * from ci_query_job where order_id=?",orderId)){
            if(!completion.completeFailure(job,"stopped","stopped",text(job.get("provider_request_id")),cut(reason,500))){
                jdbc.update("update ci_query_job set state='stopped',next_run_at=null,locked_until=null,lock_owner=null,completed_at=?,updated_at=? where id=? and state not in ('succeeded','failed','no_data','timed_out','submit_unknown','direct_unknown','stopped')",LocalDateTime.now(),LocalDateTime.now(),job.get("id"));
            }
        }
    }

    private String text(Object value){return value==null?"":String.valueOf(value).trim();}
    private int number(Object value){return number(value,0);}
    private int number(Object value,int fallback){try{return value instanceof Number n?n.intValue():Integer.parseInt(text(value));}catch(Exception ignored){return fallback;}}
    private String cut(String value,int max){String v=text(value);return v.length()<=max?v:v.substring(0,max);}
}

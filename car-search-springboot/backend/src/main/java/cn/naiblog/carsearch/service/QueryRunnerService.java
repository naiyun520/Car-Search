package cn.naiblog.carsearch.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 查询入口只入队；任何情况下均不得回退到请求线程中向供应商下单。 */
@Service
public class QueryRunnerService {
    private final JdbcTemplate jdbc;
    private final QueryJobService jobs;
    public QueryRunnerService(JdbcTemplate jdbc,QueryJobService jobs){this.jdbc=jdbc;this.jobs=jobs;}
    public Map<String,Object> run(Map<String,Object> order){
        if(!"paid".equals(order.get("status")))return Map.of("status",order.get("status"));
        Map<String,Object> job=jobs.enqueuePaidOrder(((Number)order.get("id")).longValue());
        return Map.of("status",job.getOrDefault("status","querying"),"job_state",job.getOrDefault("state","unknown"));
    }
    public Map<String,Object> retry(String no){
        Map<String,Object> order=jdbc.queryForMap("select * from ci_order where order_no=?",no);
        List<Map<String,Object>> rows=jdbc.queryForList("select id,state from ci_query_job where order_id=?",order.get("id"));
        if(!rows.isEmpty()){
            Map<String,Object> job=rows.getFirst();
            if("querying".equals(order.get("status"))&&"waiting_result".equals(job.get("state"))){
                jdbc.update("update ci_query_job set next_run_at=?,updated_at=? where id=? and state='waiting_result'",LocalDateTime.now(),LocalDateTime.now(),job.get("id"));
            }
            return Map.of("status",order.get("status"),"job_state",job.get("state"),"message","仅检查已提交任务，不会重复下单");
        }
        if("paid".equals(order.get("status")))return run(order);
        return Map.of("status",order.get("status"),"message","没有可继续处理的任务，不允许重新向供应商下单");
    }
}

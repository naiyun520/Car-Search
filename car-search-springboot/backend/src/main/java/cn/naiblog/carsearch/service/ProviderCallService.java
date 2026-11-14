package cn.naiblog.carsearch.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/** 供应商调用审计。只保存结构摘要，不保存请求或响应原文。 */
@Service
public class ProviderCallService {
    private final JdbcTemplate jdbc;
    public ProviderCallService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public long start(Map<String,Object> job,String stage,String billingStatus,BigDecimal cost){
        LocalDateTime now=LocalDateTime.now();
        org.springframework.jdbc.support.GeneratedKeyHolder keys=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection->{
            var statement=connection.prepareStatement("insert into ci_provider_call(order_id,job_id,provider_id,service_id,stage,call_status,billing_status,cost_amount,started_at,created_at) values(?,?,?,?,?,'started',?,?,?,?)",java.sql.Statement.RETURN_GENERATED_KEYS);
            Object[] values={job.get("order_id"),job.get("id"),job.get("provider_id"),job.get("service_id"),stage,billingStatus,cost==null?BigDecimal.ZERO:cost,now,now};
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);
            return statement;
        },keys);
        return java.util.Objects.requireNonNull(keys.getKey()).longValue();
    }

    public void succeed(long id,String billingStatus,String requestId,String businessCode,LocalDateTime started){
        LocalDateTime now=LocalDateTime.now();
        jdbc.update("update ci_provider_call set call_status='succeeded',billing_status=?,provider_request_id=?,business_code=?,duration_ms=?,finished_at=? where id=?",billingStatus,cut(requestId,100),cut(businessCode,80),millis(started,now),now,id);
    }

    public void fail(long id,String callStatus,String billingStatus,String requestId,String businessCode,String errorClass,String message,LocalDateTime started){
        LocalDateTime now=LocalDateTime.now();
        jdbc.update("update ci_provider_call set call_status=?,billing_status=?,provider_request_id=?,business_code=?,duration_ms=?,error_class=?,error_message=?,finished_at=? where id=?",callStatus,billingStatus,cut(requestId,100),cut(businessCode,80),millis(started,now),cut(errorClass,32),cut(message,500),now,id);
    }

    public void expireStarted(Object jobId,String billingStatus,String errorClass,String message){
        LocalDateTime now=LocalDateTime.now();
        jdbc.update("update ci_provider_call set call_status='unknown',billing_status=?,error_class=?,error_message=?,finished_at=? where job_id=? and call_status='started'",billingStatus,cut(errorClass,32),cut(message,500),now,jobId);
    }

    private long millis(LocalDateTime start,LocalDateTime end){return start==null?0:Math.max(0,Duration.between(start,end).toMillis());}
    private String cut(String value,int max){String v=value==null?"":value.replaceAll("[\\r\\n]+"," ").trim();return v.length()<=max?v:v.substring(0,max);}
}

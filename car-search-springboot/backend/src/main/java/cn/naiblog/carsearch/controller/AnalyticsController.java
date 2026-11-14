package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.*;
import cn.naiblog.carsearch.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Read-only analytics: event cash flow and creation-cohort conversion are deliberately separate. */
@RestController
@RequestMapping("/admin-api/analytics")
public class AnalyticsController {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public AnalyticsController(JdbcTemplate jdbc, AuditService audit){this.jdbc=jdbc;this.audit=audit;}
    record Range(LocalDate from, LocalDate to) {
        long days(){return ChronoUnit.DAYS.between(from,to)+1;}
    }
    static Range range(String start,String end){
        LocalDate to,from;
        try{to=end==null||end.isBlank()?LocalDate.now(ZoneId.of("Asia/Shanghai")):LocalDate.parse(end);}
        catch(Exception e){throw new BusinessException("统计结束日期：请填写有效日期，例如 2026-11-04。");}
        try{from=start==null||start.isBlank()?to:LocalDate.parse(start);}
        catch(Exception e){throw new BusinessException("统计开始日期：请填写有效日期，例如 2026-11-01。");}
        Range r=new Range(from,to);
        if(r.days()<1)throw new BusinessException("统计日期范围：开始日期不能晚于结束日期，请调整起止日期。");
        if(r.days()>366)throw new BusinessException("统计日期范围：当前选择"+r.days()+"天，单次最多366天，请缩小日期范围。");
        return r;
    }
    private List<Map<String,Object>> rows(String sql,Range r){
        return jdbc.queryForList(sql,r.from.atStartOfDay(),r.to.plusDays(1).atStartOfDay());
    }
    @GetMapping
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ApiResponse<Map<String,Object>> overview(@RequestParam(required=false)String start,@RequestParam(required=false)String end){
        Range r=range(start,end);
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("start",r.from.toString());data.put("end",r.to.toString());
        data.put("updated_at",ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
        data.put("current",summary(r));
        data.put("previous",summary(new Range(r.from.minusDays(r.days()),r.from.minusDays(1))));
        data.put("daily",daily(r));data.put("services",services(r));
        data.put("statuses",rows("select status name,count(*) as amount_value from ci_order where created_at>=? and created_at<? group by status",r));
        data.put("failures",rows("select coalesce(nullif(provider_code,''),'unknown') name,count(*) as `value` from ci_order where created_at>=? and created_at<? and status='query_failed' group by provider_code order by `value` desc",r));
        data.put("platforms",rows("select coalesce(nullif(client_platform,''),'unknown') name,count(*) as amount_value from ci_order where created_at>=? and created_at<? group by client_platform",r));
        data.put("users",jdbc.queryForObject("select count(*) from ci_user",Long.class));
        return ApiResponse.ok(data);
    }
    private Map<String,Object> summary(Range r){
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("new_users",rows("select count(*) n from ci_user where created_at>=? and created_at<?",r).getFirst().get("n"));
        m.put("orders",rows("select count(*) n from ci_order where created_at>=? and created_at<?",r).getFirst().get("n"));
        Map<String,Object> paid=rows("select count(*) paid_orders,coalesce(sum(amount),0) gross,count(distinct user_id) payers from ci_order where status<>'payment_review' and paid_at>=? and paid_at<?",r).getFirst();
        m.putAll(paid);
        Map<String,Object> cohort=rows("""
            select count(*) cohort_orders,coalesce(sum(paid_at is not null and status<>'payment_review'),0) cohort_paid,
            coalesce(sum(status='success' or exists(select 1 from ci_payment sp where sp.order_id=o.id and sp.refund_from_status='success') or exists(select 1 from ci_query_job sj where sj.order_id=o.id and sj.state='succeeded')),0) cohort_success,
            coalesce(sum(exists(select 1 from ci_payment p where p.order_id=o.id and p.refunded_at is not null) or manual_refund_time is not null),0) cohort_refunded
            from ci_order o where created_at>=? and created_at<?
            """,r).getFirst();
        m.putAll(cohort);
        m.put("repeat_payers",rows("select count(*) n from (select user_id from ci_order where status<>'payment_review' and paid_at>=? and paid_at<? group by user_id having count(*)>=2) t",r).getFirst().get("n"));
        BigDecimal refunds=sum("select coalesce(sum(refund_amount),0) n from ci_payment where refunded_at>=? and refunded_at<?",r)
            .add(sum("select coalesce(sum(manual_refund_amount),0) n from ci_order o where manual_refund_time>=? and manual_refund_time<? and not exists(select 1 from ci_payment p where p.order_id=o.id and p.refunded_at is not null)",r));
        BigDecimal cost=sum("select coalesce(sum(cost_amount),0) n from ci_provider_call where billing_status='confirmed' and started_at>=? and started_at<?",r)
            .add(sum("select coalesce(sum(cost_amount),0) n from ci_order where not exists(select 1 from ci_provider_call pc where pc.order_id=ci_order.id) and queried_at>=? and queried_at<? and (provider_request_id is not null or status='success' or result_cipher is not null)",r));
        BigDecimal unknown=sum("select coalesce(sum(cost_amount),0) n from ci_provider_call where billing_status='unknown' and started_at>=? and started_at<?",r);
        m.put("refunds",refunds);m.put("cost",cost);m.put("unknown_cost",unknown);
        m.put("net",decimal(m.get("gross")).subtract(refunds));
        m.put("profit",decimal(m.get("gross")).subtract(refunds).subtract(cost));
        m.put("payment_conversion",ratio(m.get("cohort_paid"),m.get("cohort_orders")));
        m.put("refund_rate",ratio(m.get("cohort_refunded"),m.get("cohort_paid")));
        m.put("repeat_rate",ratio(m.get("repeat_payers"),m.get("payers")));
        m.put("average",decimal(m.get("paid_orders")).signum()==0?null:decimal(m.get("gross")).divide(decimal(m.get("paid_orders")),2,RoundingMode.HALF_UP));
        return m;
    }
    private BigDecimal sum(String sql,Range r){return decimal(rows(sql,r).getFirst().get("n"));}
    static BigDecimal decimal(Object v){return v==null?BigDecimal.ZERO:new BigDecimal(v.toString());}
    static BigDecimal ratio(Object n,Object d){return decimal(d).signum()==0?null:decimal(n).multiply(BigDecimal.valueOf(100)).divide(decimal(d),1,RoundingMode.HALF_UP);}
    private List<Map<String,Object>> daily(Range r){
        // Group in SQL, then fill empty dates. No per-day network or supplier calls.
        Map<String,Map<String,Object>> days=new LinkedHashMap<>();
        for(LocalDate d=r.from;!d.isAfter(r.to);d=d.plusDays(1)){
            Map<String,Object> row=new LinkedHashMap<>();row.put("date",d.toString());
            for(String key:List.of("orders","new_users","gross","refunds","cost","unknown_cost"))row.put(key,BigDecimal.ZERO);
            days.put(d.toString(),row);
        }
        merge(days,rows("select date(created_at) as event_date,count(*) orders from ci_order where created_at>=? and created_at<? group by date(created_at)",r));
        merge(days,rows("select date(created_at) as event_date,count(*) new_users from ci_user where created_at>=? and created_at<? group by date(created_at)",r));
        merge(days,rows("select date(paid_at) as event_date,sum(amount) gross from ci_order where status<>'payment_review' and paid_at>=? and paid_at<? group by date(paid_at)",r));
        add(days,rows("select date(refunded_at) as event_date,sum(refund_amount) as amount_value from ci_payment where refunded_at>=? and refunded_at<? group by date(refunded_at)",r),"refunds");
        add(days,rows("select date(manual_refund_time) as event_date,sum(manual_refund_amount) as amount_value from ci_order o where manual_refund_time>=? and manual_refund_time<? and not exists(select 1 from ci_payment p where p.order_id=o.id and p.refunded_at is not null) group by date(manual_refund_time)",r),"refunds");
        add(days,rows("select date(started_at) as event_date,sum(cost_amount) as amount_value from ci_provider_call where billing_status='confirmed' and started_at>=? and started_at<? group by date(started_at)",r),"cost");
        add(days,rows("select date(queried_at) as event_date,sum(cost_amount) as amount_value from ci_order where not exists(select 1 from ci_provider_call pc where pc.order_id=ci_order.id) and queried_at>=? and queried_at<? and (provider_request_id is not null or status='success' or result_cipher is not null) group by date(queried_at)",r),"cost");
        add(days,rows("select date(started_at) as event_date,sum(cost_amount) as amount_value from ci_provider_call where billing_status='unknown' and started_at>=? and started_at<? group by date(started_at)",r),"unknown_cost");
        for(var row:days.values()){row.put("net",decimal(row.get("gross")).subtract(decimal(row.get("refunds"))));row.put("profit",decimal(row.get("net")).subtract(decimal(row.get("cost"))));}
        return new ArrayList<>(days.values());
    }
    private void merge(Map<String,Map<String,Object>> days,List<Map<String,Object>> rows){for(var row:rows){var target=days.get(String.valueOf(row.get("event_date")));if(target!=null)row.forEach((k,v)->{if(!k.equals("event_date"))target.put(k,v);});}}
    private void add(Map<String,Map<String,Object>> days,List<Map<String,Object>> rows,String key){for(var row:rows){var target=days.get(String.valueOf(row.get("event_date")));if(target!=null)target.put(key,decimal(target.get(key)).add(decimal(row.get("amount_value"))));}}
    private List<Map<String,Object>> services(Range r){
        List<Map<String,Object>> cohort=rows("""
            select service_id,max(service_name) service_name,count(*) orders,sum(paid_at is not null and status<>'payment_review') paid_orders,
            coalesce(sum(case when paid_at is not null and status<>'payment_review' then amount else 0 end),0) gross,
            sum(status='success') success_orders,sum(status='query_failed') failed_orders
            from ci_order where created_at>=? and created_at<? group by service_id order by paid_orders desc,orders desc
            """,r);
        Map<String,Map<String,Object>> all=new LinkedHashMap<>();
        for(var row:cohort)all.put(String.valueOf(row.get("service_id")),new LinkedHashMap<>(row));
        String[][] queries={
            {"event_gross","select service_id,max(service_name) service_name,sum(amount) amount_value from ci_order where status<>'payment_review' and paid_at>=? and paid_at<? group by service_id"},
            {"event_refunds","select o.service_id,max(o.service_name) service_name,sum(p.refund_amount) amount_value from ci_payment p join ci_order o on o.id=p.order_id where p.refunded_at>=? and p.refunded_at<? group by o.service_id"},
            {"event_refunds","select service_id,max(service_name) service_name,sum(manual_refund_amount) amount_value from ci_order o where manual_refund_time>=? and manual_refund_time<? and not exists(select 1 from ci_payment p where p.order_id=o.id and p.refunded_at is not null) group by service_id"},
            {"event_cost","select c.service_id,max(o.service_name) service_name,sum(c.cost_amount) amount_value from ci_provider_call c left join ci_order o on o.id=c.order_id where c.billing_status='confirmed' and c.started_at>=? and c.started_at<? group by c.service_id"},
            {"event_cost","select service_id,max(service_name) service_name,sum(cost_amount) amount_value from ci_order where not exists(select 1 from ci_provider_call pc where pc.order_id=ci_order.id) and queried_at>=? and queried_at<? and (provider_request_id is not null or status='success' or result_cipher is not null) group by service_id"}
        };
        for(String[] query:queries)for(var event:rows(query[1],r)){
            String id=String.valueOf(event.get("service_id"));
            var target=all.computeIfAbsent(id,k->{Map<String,Object> row=new LinkedHashMap<>();row.put("service_id",event.get("service_id"));row.put("service_name",event.get("service_name"));for(String field:List.of("orders","paid_orders","gross","success_orders","failed_orders"))row.put(field,0);return row;});
            target.put(query[0],decimal(target.get(query[0])).add(decimal(event.get("amount_value"))));
        }
        for(var row:all.values()){
            for(String k:List.of("event_gross","event_refunds","event_cost"))row.putIfAbsent(k,BigDecimal.ZERO);
            row.put("event_profit",decimal(row.get("event_gross")).subtract(decimal(row.get("event_refunds"))).subtract(decimal(row.get("event_cost"))));
        }
        return new ArrayList<>(all.values());
    }
    static String csvCell(Object value){
        String s=value==null?"":value.toString();
        if(!(value instanceof Number) && (s.matches("(?s)^[\\s]*[=+@-].*")||s.startsWith("\t")||s.startsWith("\r")))s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
    @GetMapping("/export")
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ResponseEntity<byte[]> export(@RequestParam String start,@RequestParam String end,@RequestParam(defaultValue="daily")String type,HttpServletRequest request){
        Range r=range(start,end);
        List<Map<String,Object>> data;
        LinkedHashMap<String,String> columns=new LinkedHashMap<>();
        if(type.equals("daily")){
            data=daily(r);
            String[] keys={"date","new_users","orders","gross","refunds","net","cost","unknown_cost","profit"};
            String[] labels={"日期","新增用户","创建订单","付款金额","退款金额","净收入","计入统计的估算接口成本","计费待核实的估算成本","估算毛利"};
            for(int i=0;i<keys.length;i++)columns.put(keys[i],labels[i]);
        }else if(type.equals("services")){
            data=services(r);
            String[] keys={"service_id","service_name","orders","paid_orders","gross","success_orders","failed_orders","event_gross","event_refunds","event_cost","event_profit"};
            String[] labels={"服务ID","服务名称","创建订单","已付款订单","这些订单的付款金额","这些订单查询成功数","这些订单查询失败数","日期内收到付款","日期内完成退款","日期内估算接口成本","日期内预估毛利"};
            for(int i=0;i<keys.length;i++)columns.put(keys[i],labels[i]);
        }else if(type.equals("calls")){
            data=rows("select o.order_no,c.service_id,c.provider_id,c.stage,c.call_status,c.billing_status,c.cost_amount,c.provider_request_id,c.business_code,c.started_at,c.finished_at from ci_provider_call c left join ci_order o on o.id=c.order_id where c.started_at>=? and c.started_at<? order by c.id limit 50001",r);
            if(data.size()>50000)throw new BusinessException("调用流水超过50000条，请缩小时间范围");
            String[] keys={"order_no","service_id","provider_id","stage","call_status","billing_status","cost_amount","provider_request_id","business_code","started_at","finished_at"};
            String[] labels={"订单号","服务ID","供应商ID","调用阶段","调用状态","计费判定（系统规则，非账单）","配置估算成本","供应商请求ID","业务码","开始时间","完成时间"};
            for(int i=0;i<keys.length;i++)columns.put(keys[i],labels[i]);
        }else if(type.equals("orders")){
            // Include any financial event in the period, including refunds for older orders.
            data=jdbc.queryForList("""
                select o.order_no,o.user_id,o.service_name,o.client_platform,o.amount,o.status,o.created_at,o.paid_at,o.queried_at,
                o.cost_amount configured_cost,o.fulfillment_version,p.refund_amount,p.refunded_at,o.manual_refund_amount,o.manual_refund_time,
                p.wechat_pay_transaction_id
                from ci_order o left join ci_payment p on p.order_id=o.id
                where (o.created_at>=? and o.created_at<?) or (o.paid_at>=? and o.paid_at<?)
                or (p.refunded_at>=? and p.refunded_at<?) or (o.manual_refund_time>=? and o.manual_refund_time<?)
                order by o.id limit 50001
                """,r.from.atStartOfDay(),r.to.plusDays(1).atStartOfDay(),r.from.atStartOfDay(),r.to.plusDays(1).atStartOfDay(),r.from.atStartOfDay(),r.to.plusDays(1).atStartOfDay(),r.from.atStartOfDay(),r.to.plusDays(1).atStartOfDay());
            if(data.size()>50000)throw new BusinessException("订单超过50000条，请缩小导出时间范围");
            String[] labels={"订单号","用户ID","服务","终端","订单金额","当前状态","创建时间","付款时间","查询时间","配置接口成本（估算，非实际扣款）","履约版本","在线退款金额","在线退款时间","人工退款金额","人工退款时间","微信交易号"};
            String[] keys={"order_no","user_id","service_name","client_platform","amount","status","created_at","paid_at","queried_at","configured_cost","fulfillment_version","refund_amount","refunded_at","manual_refund_amount","manual_refund_time","wechat_pay_transaction_id"};
            for(int i=0;i<keys.length;i++)columns.put(keys[i],labels[i]);
        }else throw new BusinessException("不支持的导出类型");
        StringBuilder csv=new StringBuilder("\ufeff");
        csv.append(String.join(",",columns.values().stream().map(AnalyticsController::csvCell).toList())).append("\r\n");
        for(var row:data)csv.append(String.join(",",columns.keySet().stream().map(k->csvCell((k.equals("order_no")||k.endsWith("transaction_id")||k.equals("provider_request_id")) && row.get(k)!=null ? "'"+row.get(k) : row.get(k))).toList())).append("\r\n");
        audit.record(request,"analytics.export","report",type,Map.of("start",start,"end",end,"rows",data.size()));
        return ResponseEntity.ok().header("Content-Type","text/csv; charset=UTF-8").header("Content-Disposition","attachment; filename=\""+type+"-"+start+"-"+end+".csv\"").body(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}


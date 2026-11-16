package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.*;
import cn.naiblog.carsearch.security.AdminAuthInterceptor;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.security.CurrentAdmin;
import cn.naiblog.carsearch.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/admin-api")
public class AdminOrderController {
    private final JdbcTemplate jdbc; private final CryptoService crypto; private final ObjectMapper mapper;
    private final PaymentReconciliationService payments; private final PaymentLifecycleService lifecycle; private final QueryRunnerService queries; private final QueryJobService jobs; private final AuditService audit;private final EmailNotificationService email;
    public AdminOrderController(JdbcTemplate jdbc,CryptoService crypto,ObjectMapper mapper,PaymentReconciliationService payments,PaymentLifecycleService lifecycle,QueryRunnerService queries,QueryJobService jobs,AuditService audit,EmailNotificationService email){this.jdbc=jdbc;this.crypto=crypto;this.mapper=mapper;this.payments=payments;this.lifecycle=lifecycle;this.queries=queries;this.jobs=jobs;this.audit=audit;this.email=email;}

    @GetMapping("/orders")
    public ApiResponse<Map<String,Object>>orders(@RequestParam(defaultValue="1")int page,@RequestParam(name="page_size",defaultValue="20")int size,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String keyword){
        page=Math.max(1,page);size=Math.max(1,Math.min(100,size));List<Object>args=new ArrayList<>();StringBuilder where=new StringBuilder(" where 1=1");if(!status.isBlank()){where.append(" and o.status=?");args.add(status);}if(!keyword.isBlank()){String like="%"+keyword.trim()+"%";where.append(" and (o.order_no like ? or p.order_no like ? or p.transaction_id like ? or p.wechat_order_id like ? or p.wechat_pay_transaction_id like ? or p.channel_order_id like ? or u.nickname like ? or u.phone like ?");for(int i=0;i<8;i++)args.add(like);if(keyword.matches("\\d+")){where.append(" or o.user_id=?");args.add(Long.parseLong(keyword));}where.append(")");}
        long total=scalar("select count(*) from ci_order o left join ci_user u on u.id=o.user_id left join ci_payment p on p.order_id=o.id"+where,args.toArray());List<Object>pageArgs=new ArrayList<>(args);pageArgs.add(size);pageArgs.add((page-1)*size);
        List<Map<String,Object>>rows=jdbc.queryForList("select o.id,o.order_no,o.user_id,o.service_name,o.amount,o.status,o.paid_at,o.queried_at,o.created_at,u.nickname,u.phone,p.wechat_order_type,p.wechat_order_id,p.wechat_pay_transaction_id,p.channel_order_id,p.delivery_status,p.delivery_attempts,p.delivery_last_error,p.delivered_at,p.refund_status,p.refund_source,p.refund_order_no,p.refund_amount,p.refund_reason,p.refund_from_status,p.refund_last_error,p.refund_requested_at,p.refunded_at from ci_order o left join ci_user u on u.id=o.user_id left join ci_payment p on p.order_id=o.id"+where+" order by o.id desc limit ? offset ?",pageArgs.toArray());
        attachOrderBalances(rows);rows.forEach(this::decorate);return ApiResponse.ok(Pagination.of(rows,total,page,size));
    }

    @GetMapping("/order-detail/{orderNo}")
    public ApiResponse<Map<String,Object>>detail(@PathVariable String orderNo){List<Map<String,Object>>rows=jdbc.queryForList("select o.*,u.nickname,u.phone,p.transaction_id,p.wechat_order_id,p.wechat_pay_transaction_id,p.channel_order_id,p.wechat_order_type,p.status payment_status,p.pay_env,p.callback_payload,p.last_checked_at,p.delivery_status,p.delivery_attempts,p.delivery_attempted_at,p.delivery_last_error,p.delivered_at,p.refund_status,p.refund_source,p.refund_order_no,p.refund_amount,p.refund_reason,p.refund_last_error,p.refund_requested_at,p.refunded_at from ci_order o left join ci_user u on u.id=o.user_id left join ci_payment p on p.order_id=o.id where o.order_no=?",orderNo);if(rows.isEmpty())throw new BusinessException(404,HttpStatus.NOT_FOUND,"订单不存在");attachOrderBalances(rows);Map<String,Object>row=rows.getFirst();row.put("input",decrypt(text(row.get("input_cipher")),"订单查询输入"));Map<String,Object>result=decrypt(text(row.get("result_cipher")),"订单查询结果");row.put("result",ResultValueFormatter.formatItems(result.getOrDefault("items",List.of())));row.put("editable_result",result.getOrDefault("items",List.of()));Map<String,Object>wx=jsonMap(text(row.get("callback_payload")));wx.remove("token");row.put("wechat_order",wx);row.put("refund_reason_label",refundReason(text(row.get("refund_reason"))));for(String k:List.of("input_cipher","result_cipher","service_snapshot_cipher","callback_payload"))row.remove(k);return ApiResponse.ok(row);}

    @GetMapping("/orders/{orderNo}/result-revisions")
    public ApiResponse<List<Map<String,Object>>> resultRevisions(@PathVariable String orderNo){
        Map<String,Object> order=orderByNo(orderNo);
        return ApiResponse.ok(jdbc.queryForList("select id,base_revision_id,source,change_reason,status,created_by,created_at,published_at from ci_order_result_revision where order_id=? order by id desc",order.get("id")));
    }

    @GetMapping("/orders/{orderNo}/result-revisions/{revisionId}")
    public ApiResponse<Map<String,Object>> revisionDetail(@PathVariable String orderNo,@PathVariable long revisionId){
        Map<String,Object> order=orderByNo(orderNo);
        List<Map<String,Object>> rows=jdbc.queryForList("select * from ci_order_result_revision where order_id=? and id=?",order.get("id"),revisionId);
        if(rows.isEmpty())throw new BusinessException("修订不存在");
        Map<String,Object> row=rows.getFirst();
        row.put("result",crypto.decrypt(text(row.remove("result_cipher"))));
        return ApiResponse.ok(row);
    }

    @PostMapping("/orders/{orderNo}/result-revisions")
    @Transactional
    public ApiResponse<Map<String,Object>> createResultRevision(@PathVariable String orderNo,@RequestBody Map<String,Object> body,HttpServletRequest req){
        Map<String,Object> order=orderByNo(orderNo);String reason=cut(text(body.get("reason")),500);
        if(!"success".equals(text(order.get("status"))))throw new BusinessException("仅成功订单可修订结果");
        if(order.get("result_cipher")==null||text(order.get("result_cipher")).isBlank())throw new BusinessException("结果已清理，无法修订");
        if(reason.isBlank())throw new BusinessException("必须填写修订原因");
        Object raw=body.containsKey("items")?body.get("items"):body.get("result");
        List<Map<String,Object>> items=revisionItems(raw);
        CurrentAdmin admin=(CurrentAdmin)req.getAttribute(AdminAuthInterceptor.ATTRIBUTE);
        LocalDateTime now=LocalDateTime.now();
        jdbc.update("insert into ci_order_result_revision(order_id,base_revision_id,source,result_cipher,change_reason,status,created_by,created_at) values(?,?, 'manual',?,?, 'draft',?,?)",order.get("id"),order.get("active_result_revision_id"),crypto.encrypt(Map.of("items",items)),reason,admin==null?null:admin.id(),now);
        long id=jdbc.queryForObject("select last_insert_id()",Long.class);
        audit.record(req,"order.result_revision.create","order",orderNo,Map.of("revision_id",id,"item_count",items.size()));
        return ApiResponse.ok(Map.of("id",id,"status","draft"));
    }

    @PostMapping("/orders/{orderNo}/result-revisions/{revisionId}/publish")
    @Transactional
    public ApiResponse<Map<String,Object>> publishResultRevision(@PathVariable String orderNo,@PathVariable long revisionId,HttpServletRequest req){
        Map<String,Object> order=orderByNo(orderNo);
        List<Map<String,Object>> rows=jdbc.queryForList("select * from ci_order_result_revision where id=? and order_id=? and status='draft' for update",revisionId,order.get("id"));
        if(rows.isEmpty())throw new BusinessException("待发布的结果修订不存在");
        Map<String,Object> revision=rows.getFirst();LocalDateTime now=LocalDateTime.now();
        // Serialize publishers on the order; stale drafts must not overwrite later corrections.
        order=jdbc.queryForMap("select * from ci_order where id=? for update",order.get("id"));
        if(!Objects.equals(order.get("active_result_revision_id"),revision.get("base_revision_id")))throw new BusinessException("结果已被其他人修订，请重新加载后创建草稿");
        if(order.get("result_cipher")==null)throw new BusinessException("结果已清理，不能发布");
        if(order.get("active_result_revision_id")==null){
            jdbc.update("insert into ci_order_result_revision(order_id,source,result_cipher,change_reason,status,created_at,published_at) values(?,'original',?,'首次修订前原始结果','published',?,?)",order.get("id"),order.get("result_cipher"),now,now);
        }
        int changed=jdbc.update("update ci_order set result_cipher=?,active_result_revision_id=?,updated_at=? where id=? and status='success'",revision.get("result_cipher"),revisionId,now,order.get("id"));
        if(changed!=1)throw new BusinessException("只有成功订单可以发布结果修订，订单状态不会被此操作改变");
        jdbc.update("update ci_order_result_revision set status='published',published_at=? where id=? and status='draft'",now,revisionId);
        audit.record(req,"order.result_revision.publish","order",orderNo,Map.of("revision_id",revisionId));
        return ApiResponse.ok(Map.of("id",revisionId,"status","published"));
    }

    @GetMapping("/orders/{orderNo}/fulfillment")
    public ApiResponse<Map<String,Object>> fulfillment(@PathVariable String orderNo){
        Map<String,Object> order=orderByNo(orderNo);
        List<Map<String,Object>> task=jdbc.queryForList("select * from ci_query_job where order_id=?",order.get("id"));
        List<Map<String,Object>> calls=jdbc.queryForList("select id,provider_id,stage,call_status,billing_status,cost_amount,provider_request_id,business_code,duration_ms,error_class,error_message,started_at,finished_at from ci_provider_call where order_id=? order by id desc",order.get("id"));
        attachCallBalances(calls);
        return ApiResponse.ok(Map.of("task",task.isEmpty()?Map.of():task.getFirst(),"calls",calls));
    }

    @PostMapping("/orders/{orderNo}/fulfillment/stop")
    public ApiResponse<Map<String,Object>> stopFulfillment(@PathVariable String orderNo,@RequestBody(required=false)Map<String,Object> body,HttpServletRequest req){
        Map<String,Object> order=orderByNo(orderNo);String reason=body==null?"manual stop":cut(text(body.get("reason")),500);if(reason.isBlank())reason="manual stop";
        jobs.stop(((Number)order.get("id")).longValue(),reason);audit.record(req,"order.fulfillment.stop","order",orderNo,Map.of("reason",reason));
        return ApiResponse.ok(Map.of("status","query_failed"));
    }

    @PostMapping("/orders/{orderNo}/fulfillment/fetch-now")
    public ApiResponse<Map<String,Object>> fetchNow(@PathVariable String orderNo,HttpServletRequest req){
        Map<String,Object> order=orderByNo(orderNo);int changed=jdbc.update("update ci_query_job set next_run_at=?,updated_at=? where order_id=? and state='waiting_result'",LocalDateTime.now(),LocalDateTime.now(),order.get("id"));
        if(changed!=1)throw new BusinessException("当前任务不处于可获取报告状态");audit.record(req,"order.fulfillment.fetch_now","order",orderNo,Map.of());
        return ApiResponse.ok(Map.of("queued",true));
    }

    @PostMapping("/orders/{orderNo}/sync-payment") public ApiResponse<Map<String,Object>>sync(@PathVariable String orderNo,HttpServletRequest req){Map<String,Object>row=paymentOrder(orderNo);String current=text(row.get("status"));if(!Set.of("pending_payment","payment_review").contains(current))return ApiResponse.ok(Map.of("status",current),"订单状态无需同步");String state=payments.reconcile(row,row);if("paid".equals(state)){Map<String,Object>fresh=jdbc.queryForMap("select * from ci_order where order_no=?",orderNo);state=text(queries.run(fresh).get("status"));}audit.record(req,"order.payment.sync","order",orderNo,Map.of("status",state));return ApiResponse.ok(Map.of("status",state),"success".equals(state)?"支付已确认并完成查询":"微信支付状态已同步");}
    @PostMapping("/orders/{orderNo}/retry") public ApiResponse<Map<String,Object>>retry(@PathVariable String orderNo,HttpServletRequest req){Map<String,Object>result=queries.retry(orderNo);audit.record(req,"order.retry","order",orderNo,Map.of("status",result.get("status")));return ApiResponse.ok(result,"订单查询已重试");}
    @PostMapping("/orders/{orderNo}/delivery") public ApiResponse<Map<String,Object>>delivery(@PathVariable String orderNo,HttpServletRequest req){String state=lifecycle.deliver(orderNo,true);audit.record(req,"order.delivery.retry","order",orderNo,Map.of("status",state));return ApiResponse.ok(Map.of("status",state),"微信发货状态已确认");}
    @PostMapping("/orders/{orderNo}/refund") public ApiResponse<Map<String,Object>>refund(@PathVariable String orderNo,@RequestBody(required=false)Map<String,Object>b,HttpServletRequest req){String reason=b==null?"2":text(b.getOrDefault("reason","2"));String state=lifecycle.requestRefund(orderNo,reason);audit.record(req,"order.refund","order",orderNo,Map.of("reason",reason,"status",state));return ApiResponse.ok(Map.of("status",state),"refunded".equals(state)?"订单已退款":"退款申请已提交微信处理");}
    @PostMapping("/orders/{orderNo}/refund-status") public ApiResponse<Map<String,Object>>refundStatus(@PathVariable String orderNo,HttpServletRequest req){String state=lifecycle.reconcileRefund(orderNo);audit.record(req,"order.refund.reconcile","order",orderNo,Map.of("status",state));return ApiResponse.ok(Map.of("status",state),"refunded".equals(state)?"退款已完成":"failed".equals(state)?"退款失败，可重新申请":"退款仍在处理中");}

    @PostMapping("/orders/batch-delete") @Transactional public ApiResponse<Object>deleteOrders(@RequestBody Map<String,Object>b,HttpServletRequest req){List<?>raw=b.get("order_nos")instanceof List<?>l?l:b.get("ids")instanceof List<?>l?l:List.of();List<String>nos=raw.stream().map(this::text).filter(v->!v.isBlank()).distinct().toList();if(nos.isEmpty())throw new BusinessException("请选择要删除的订单");if(nos.size()>200)throw new BusinessException("单次最多删除200笔订单");String marks=String.join(",",Collections.nCopies(nos.size(),"?"));List<Map<String,Object>>rows=jdbc.queryForList("select o.id,o.order_no,o.paid_at,p.status payment_status from ci_order o left join ci_payment p on p.order_id=o.id where o.order_no in ("+marks+")",nos.toArray());for(Map<String,Object>row:rows)if(row.get("paid_at")!=null||!Set.of("created","cancelled","").contains(text(row.get("payment_status"))))throw new BusinessException("已付款或进入支付处理的订单属于财务凭证，不能物理删除");for(Map<String,Object>row:rows){jdbc.update("delete from ci_payment where order_id=?",row.get("id"));jdbc.update("delete from ci_order where id=?",row.get("id"));}audit.record(req,"order.batch_delete","order",nos.getFirst(),Map.of("order_nos",nos,"count",rows.size()));return ApiResponse.ok(null,"已删除 "+rows.size()+" 笔订单");}

    @GetMapping("/wechat-messages") public ApiResponse<Map<String,Object>>messages(@RequestParam(defaultValue="1")int page,@RequestParam(name="page_size",defaultValue="20")int size,@RequestParam(defaultValue="")String type,@RequestParam(defaultValue="")String status){page=Math.max(1,page);size=Math.max(1,Math.min(100,size));List<Object>args=new ArrayList<>();StringBuilder where=new StringBuilder(" where 1=1");if(!type.isBlank()){where.append(" and event_type=?");args.add(type);}if(!status.isBlank()){where.append(" and process_status=?");args.add(status);}long total=scalar("select count(*) from ci_wechat_event"+where,args.toArray());List<Object>all=new ArrayList<>(args);all.add(size);all.add((page-1)*size);List<Map<String,Object>>rows=jdbc.queryForList("select * from ci_wechat_event"+where+" order by id desc limit ? offset ?",all.toArray());for(Map<String,Object>row:rows){Map<String,Object>request=crypto.decrypt(text(row.get("request_cipher"))),response=crypto.decrypt(text(row.get("response_cipher")));if(!text(row.get("request_cipher")).isBlank()&&request.isEmpty()&&row.get("last_error")==null)row.put("last_error","历史消息无法使用当前主密钥解密");row.put("summary",summary(text(row.get("event_type")),request));row.put("request",request);row.put("response",response);for(String k:List.of("request_cipher","response_cipher","event_key"))row.remove(k);}return ApiResponse.ok(Pagination.of(rows,total,page,size));}

    private Map<String,Object> orderByNo(String no){List<Map<String,Object>> rows=jdbc.queryForList("select * from ci_order where order_no=?",no);if(rows.isEmpty())throw new BusinessException(404,HttpStatus.NOT_FOUND,"订单不存在");return rows.getFirst();}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> revisionItems(Object raw){
        Object items=raw instanceof Map<?,?> map?map.get("items"):raw;
        if(!(items instanceof List<?> list)||list.isEmpty()||list.size()>500)throw new BusinessException("修订结果必须包含 1-500 个展示字段");
        List<Map<String,Object>> out=new ArrayList<>();Map<String,Integer> positions=new HashMap<>();int row=0;
        for(Object item:list){row++;if(!(item instanceof Map<?,?> map))throw new BusinessException("结果修订第"+row+"行：内容无法读取，请重新添加这一行");String key=text(map.get("key")),label=text(map.get("label"));if(key.isBlank()||key.length()>100)throw new BusinessException("结果修订第"+row+"行：字段标识须填写1至100个字符");if(label.isBlank()||label.length()>100)throw new BusinessException("结果修订第"+row+"行：展示名称须填写1至100个字符");if(positions.containsKey(key))throw new BusinessException("结果修订第"+row+"行：字段标识“"+key+"”与第"+positions.get(key)+"行重复，请修改或删除其中一行");positions.put(key,row);Map<String,Object> safe=new LinkedHashMap<>();safe.put("key",key);safe.put("label",label);safe.put("value",map.containsKey("value")?map.get("value"):"");for(String extra:List.of("display","columns","type","unit","group"))if(map.containsKey(extra))safe.put(extra,map.get(extra));out.add(safe);}return out;
    }
    private Map<String,Object>paymentOrder(String no){List<Map<String,Object>>rows=jdbc.queryForList("select o.*,u.openid from ci_order o join ci_user u on u.id=o.user_id where o.order_no=?",no);if(rows.isEmpty())throw new BusinessException(404,HttpStatus.NOT_FOUND,"订单不存在");return rows.getFirst();}
    private void decorate(Map<String,Object>r){int type=number(r.get("wechat_order_type"),-1);r.put("payment_platform",type==7||type==8?"ios":type==0||type==1?"wechat":"unknown");String status=text(r.get("status"));r.put("fulfillment_status",Set.of("refunding","refunded").contains(status)?(!text(r.get("refund_from_status")).isBlank()?r.get("refund_from_status"):"delivered".equals(text(r.get("delivery_status")))?"success":"query_failed"):status);}
    private void attachOrderBalances(List<Map<String,Object>> rows){attachBalances(rows,"id","order_id");}
    private void attachCallBalances(List<Map<String,Object>> rows){attachBalances(rows,"id","provider_call_id");}
    private void attachBalances(List<Map<String,Object>> targets,String targetId,String snapshotLink){
        List<Long> ids=targets.stream().map(r->longNumber(r.get(targetId))).filter(v->v>0).distinct().toList();
        if(ids.isEmpty())return;
        String marks=String.join(",",Collections.nCopies(ids.size(),"?"));
        List<Map<String,Object>> snapshots=jdbc.queryForList("select s."+snapshotLink+" linked_id,s.phase,s.balance,s.status,s.error_message,s.observed_at,p.name provider_name from ci_provider_balance_snapshot s left join ci_provider p on p.id=s.provider_id where s."+snapshotLink+" in ("+marks+") and s.phase in ('before','after') order by s.observed_at,s.id",ids.toArray());
        Map<Long,List<Map<String,Object>>> grouped=new HashMap<>();
        for(Map<String,Object> snapshot:snapshots)grouped.computeIfAbsent(longNumber(snapshot.get("linked_id")),ignored->new ArrayList<>()).add(snapshot);
        for(Map<String,Object> target:targets)applyBalanceSummary(target,grouped.getOrDefault(longNumber(target.get(targetId)),List.of()));
    }
    private void applyBalanceSummary(Map<String,Object> target,List<Map<String,Object>> snapshots){
        BigDecimal before=null,after=null;boolean firstBeforeSeen=false;String providerName="",lastError="";Object observedAt=null;
        for(Map<String,Object> snapshot:snapshots){
            if(!text(snapshot.get("provider_name")).isBlank())providerName=text(snapshot.get("provider_name"));
            observedAt=snapshot.get("observed_at");
            if("failed".equals(text(snapshot.get("status")))&&!text(snapshot.get("error_message")).isBlank())lastError=text(snapshot.get("error_message"));
            boolean success="success".equals(text(snapshot.get("status")))&&snapshot.get("balance")!=null;
            if("before".equals(text(snapshot.get("phase")))&&!firstBeforeSeen){firstBeforeSeen=true;if(success)before=decimal(snapshot.get("balance"));}
            if("after".equals(text(snapshot.get("phase"))))after=success?decimal(snapshot.get("balance")):null;
        }
        target.put("provider_balance_status",before!=null&&after!=null?"captured":before!=null||after!=null?"partial":snapshots.isEmpty()?"none":"failed");
        if(before!=null)target.put("provider_balance_before",before);
        if(after!=null)target.put("provider_balance_after",after);
        if(before!=null&&after!=null)target.put("provider_balance_change",after.subtract(before));
        if(!providerName.isBlank())target.put("provider_name",providerName);
        if(observedAt!=null)target.put("provider_balance_observed_at",observedAt);
        if(!lastError.isBlank())target.put("provider_balance_error",lastError);
    }
    private long longNumber(Object value){try{return value instanceof Number n?n.longValue():Long.parseLong(text(value));}catch(Exception ignored){return 0;}}
    private BigDecimal decimal(Object value){try{return value instanceof BigDecimal b?b:new BigDecimal(text(value));}catch(Exception ignored){return BigDecimal.ZERO;}}
    private String summary(String type,Map<String,Object>p){String reason=text(first(p,"refund_request_reason","RefundRequestReason"));return switch(type){case"xpay_subscribe_ios_refund_query_notify"->"iOS 退款问询"+(reason.isBlank()?"":"："+refundReason(reason));case"xpay_refund_notify"->"退款结果："+(number(first(p,"RetCode","ret_code"),-1)==0?"成功":text(first(p,"RetMsg","ret_msg")));case"xpay_goods_deliver_notify"->"虚拟支付发货通知";case"xpay_coin_pay_notify"->"代币支付通知";case"xpay_complaint_notify"->"用户投诉："+cut(text(first(p,"ComplaintDetail","complaint_detail")),100);case"xpay_wxpay_callback_notify"->"微信支付风控通知："+cut(text(first(p,"Remark","remark")),100);default->"其他微信消息";};}
    private String refundReason(String r){return Map.of("UNINTENDED_PURCHASE","误操作购买","DID_NOT_MEAN_TO_BUY","误操作购买","ITEM_NOT_RECEIVED","未收到购买内容","ITEM_NOT_AS_DESCRIBED","内容与描述不符","QUALITY_ISSUE","质量问题","CHILD_PURCHASE","未成年人购买","OTHER","其他原因").getOrDefault(r.toUpperCase(Locale.ROOT),r);}
    private Map<String,Object>decrypt(String cipher,String label){if(cipher.isBlank())return Map.of();Map<String,Object>v=crypto.decrypt(cipher);if(v.isEmpty())throw new BusinessException(500,HttpStatus.INTERNAL_SERVER_ERROR,label+"无法解密，请检查服务器主加密密钥是否与原环境一致");return v;}
    private Map<String,Object>jsonMap(String json){if(json.isBlank())return new LinkedHashMap<>();try{return mapper.readValue(json,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){return new LinkedHashMap<>();}}
    private Object first(Map<String,Object>m,String...keys){for(String k:keys)if(m.containsKey(k))return m.get(k);return null;}private long scalar(String sql,Object...a){Long v=jdbc.queryForObject(sql,Long.class,a);return v==null?0:v;}private String text(Object v){return v==null?"":String.valueOf(v).trim();}private int number(Object v,int fallback){try{return v instanceof Number n?n.intValue():Integer.parseInt(text(v));}catch(Exception e){return fallback;}}private String cut(String v,int n){return v==null?"":v.length()<=n?v:v.substring(0,n);}
}

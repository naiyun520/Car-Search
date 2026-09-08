package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.wechat.WechatPaymentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PaymentDeliveryService {
    private final JdbcTemplate jdbc; private final WechatPaymentService wechat;
    public PaymentDeliveryService(JdbcTemplate jdbc,WechatPaymentService wechat){this.jdbc=jdbc;this.wechat=wechat;}
    public String deliver(String orderNo,boolean throwOnFailure){Map<String,Object>r=row(orderNo);if(!"success".equals(text(r.get("order_status"))))throw new BusinessException("只有查询成功的订单才能通知微信发货");String state=text(r.get("delivery_status"));if("delivered".equals(state))return state;LocalDateTime attempted=date(r.get("delivery_attempted_at"));if("processing".equals(state)&&attempted!=null&&attempted.isAfter(LocalDateTime.now().minusMinutes(1)))return state;LocalDateTime now=LocalDateTime.now();int claimed=jdbc.update("update ci_payment set delivery_status='processing',delivery_attempts=delivery_attempts+1,delivery_attempted_at=?,delivery_last_error=null,updated_at=? where id=? and delivery_status=?",now,now,r.get("payment_id"),state);if(claimed==0)return"processing";try{wechat.deliver(orderNo,number(r.get("pay_env")));mark(r,now);return"delivered";}catch(Exception first){try{Map<String,Object>w=wechat.queryOrder(text(r.get("openid")),orderNo,number(r.get("pay_env")));if(number(w.get("status"))==4){mark(r,now);return"delivered";}}catch(Exception ignored){}jdbc.update("update ci_payment set delivery_status='failed',delivery_last_error=?,updated_at=? where id=?",cut(first.getMessage(),500),LocalDateTime.now(),r.get("payment_id"));if(throwOnFailure)throw new BusinessException("微信发货确认失败，请稍后重试");return"failed";}}
    private Map<String,Object>row(String no){List<Map<String,Object>>r=jdbc.queryForList("select o.status order_status,p.id payment_id,p.pay_env,p.delivery_status,p.delivery_attempted_at,u.openid from ci_order o join ci_payment p on p.order_id=o.id join ci_user u on u.id=o.user_id where o.order_no=?",no);if(r.isEmpty())throw new BusinessException("订单不存在");return r.getFirst();}private void mark(Map<String,Object>r,LocalDateTime n){jdbc.update("update ci_payment set delivery_status='delivered',delivered_at=?,delivery_last_error=null,updated_at=? where id=?",n,n,r.get("payment_id"));}private int number(Object v){return v instanceof Number n?n.intValue():0;}private String text(Object v){return v==null?"":String.valueOf(v);}private LocalDateTime date(Object v){return v instanceof Timestamp t?t.toLocalDateTime():v instanceof LocalDateTime d?d:null;}private String cut(String v,int n){if(v==null)return"";return v.length()<=n?v:v.substring(0,n);}
}

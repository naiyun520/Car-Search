package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.wechat.WechatPaymentService;
import cn.naiblog.carsearch.config.SettingService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PaymentReconciliationService {
    private final JdbcTemplate jdbc;private final WechatPaymentService wechat;private final ObjectMapper mapper;private final SettingService settings;private final MailService mail;
    public PaymentReconciliationService(JdbcTemplate jdbc,WechatPaymentService wechat,ObjectMapper mapper,SettingService settings,MailService mail){this.jdbc=jdbc;this.wechat=wechat;this.mapper=mapper;this.settings=settings;this.mail=mail;}
    public String reconcile(Map<String,Object> order,Map<String,Object> user){String current=String.valueOf(order.get("status"));if(!List.of("pending_payment","payment_review").contains(current))return current;Map<String,Object> payment=jdbc.queryForMap("select * from ci_payment where order_id=?",order.get("id"));LocalDateTime now=LocalDateTime.now();int claimed=jdbc.update("update ci_payment set last_checked_at=? where id=? and (last_checked_at is null or last_checked_at<?)",now,payment.get("id"),now.minusSeconds(3));if(claimed==0)return current;
        Map<String,Object> wx=wechat.queryOrder(String.valueOf(user.get("openid")),String.valueOf(order.get("order_no")),((Number)payment.get("pay_env")).intValue());remember(payment,wx);
        if(Boolean.TRUE.equals(wx.get("not_found"))){if("payment_review".equals(current))return current;if(order.get("expired_at") instanceof java.sql.Timestamp t&&t.toLocalDateTime().isBefore(now)){jdbc.update("update ci_order set status='cancelled',updated_at=? where id=? and status='pending_payment'",now,order.get("id"));jdbc.update("update ci_payment set status='cancelled',updated_at=? where order_id=? and status='created'",now,order.get("id"));return"cancelled";}return current;}
        int status=((Number)wx.getOrDefault("status",0)).intValue();if(List.of(5,8).contains(status)){jdbc.update("update ci_order set status='refunded',updated_at=? where id=? and status in ('pending_payment','payment_review')",now,order.get("id"));jdbc.update("update ci_payment set status='refunded',refund_status='refunded',refund_amount=?,refunded_at=?,updated_at=? where order_id=?",order.get("amount"),now,now,order.get("id"));return"refunded";}if(status==6){jdbc.update("update ci_order set status='cancelled',updated_at=? where id=?",now,order.get("id"));jdbc.update("update ci_payment set status='cancelled',updated_at=? where order_id=?",now,order.get("id"));return"cancelled";}if(!List.of(2,3,4).contains(status))return current;
        int type=((Number)wx.getOrDefault("order_type",-1)).intValue();int expectedEnv=((Number)payment.get("pay_env")).intValue()==1?2:1;int paid=((Number)wx.getOrDefault("paid_fee",wx.getOrDefault("order_fee",-1))).intValue();int expected=new BigDecimal(String.valueOf(order.get("amount"))).movePointRight(2).intValue();if(!String.valueOf(order.get("order_no")).equals(String.valueOf(wx.getOrDefault("order_id","")))||!List.of(0,7).contains(type)||(wx.containsKey("env_type")&&((Number)wx.get("env_type")).intValue()!=expectedEnv)||paid!=expected){review(order,now);return"payment_review";}
        int changed=jdbc.update("update ci_order set status='paid',paid_at=?,updated_at=? where id=? and status in ('pending_payment','payment_review')",now,now,order.get("id"));if(changed>0){jdbc.update("update ci_payment set status='paid',transaction_id=?,wechat_order_id=?,wechat_pay_transaction_id=?,channel_order_id=?,wechat_order_type=?,paid_at=?,updated_at=? where order_id=?",first(wx,"wxpay_order_id","wx_order_id","channel_order_id"),first(wx,"wx_order_id"),first(wx,"wxpay_order_id"),first(wx,"channel_order_id"),type,now,now,order.get("id"));if("1".equals(settings.value("email_notify_payment","0"))){Map<String,Object>fresh=jdbc.queryForMap("select * from ci_order where id=?",order.get("id"));mail.notifyAll("支付成功 - "+order.get("order_no"),mail.orderTemplate("支付成功通知",fresh,"微信已确认支付"));}}return"paid";}
    private void review(Map<String,Object>o,LocalDateTime now){jdbc.update("update ci_order set status='payment_review',paid_at=?,updated_at=? where id=?",now,now,o.get("id"));jdbc.update("update ci_payment set status='review',paid_at=?,updated_at=? where order_id=?",now,now,o.get("id"));}
    private void remember(Map<String,Object>p,Map<String,Object>w){try{jdbc.update("update ci_payment set callback_payload=?,wechat_order_id=coalesce(?,wechat_order_id),wechat_pay_transaction_id=coalesce(?,wechat_pay_transaction_id),channel_order_id=coalesce(?,channel_order_id),wechat_order_type=coalesce(?,wechat_order_type),updated_at=? where id=?",mapper.writeValueAsString(w),first(w,"wx_order_id"),first(w,"wxpay_order_id"),first(w,"channel_order_id"),w.get("order_type"),LocalDateTime.now(),p.get("id"));}catch(Exception ignored){}}
    private String first(Map<String,Object>m,String...keys){for(String k:keys){String v=String.valueOf(m.getOrDefault(k,"")).trim();if(!v.isBlank())return v.length()>100?v.substring(0,100):v;}return null;}
}

package cn.naiblog.carsearch.wechat;

import cn.naiblog.carsearch.config.SettingService;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.security.Hashing;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** Processes encrypted WeChat virtual-payment events and keeps the response idempotent. */
@Service
public class WechatRefundEventService {
    static final String IOS_INQUIRY = "xpay_subscribe_ios_refund_query_notify";
    static final String REFUND_NOTIFY = "xpay_refund_notify";
    static final String GOODS_DELIVER = "xpay_goods_deliver_notify";

    private final JdbcTemplate jdbc;
    private final CryptoService crypto;
    private final SettingService settings;
    private final ObjectMapper mapper;

    public WechatRefundEventService(JdbcTemplate jdbc, CryptoService crypto, SettingService settings, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.settings = settings;
        this.mapper = mapper;
    }

    public Map<String, Object> handle(Map<String, Object> event) {
        String type = text(field(event, "Event", "event")).trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case IOS_INQUIRY -> iosInquiry(event);
            case REFUND_NOTIFY -> refundNotify(event);
            case GOODS_DELIVER -> goodsDeliver(event);
            default -> acknowledgeOther(event, type);
        };
    }

    Map<String, Object> goodsDeliver(Map<String, Object> event) {
        String orderNo = text(field(event, "OutTradeNo", "out_trade_no"));
        Map<String, Object> payInfo = map(field(event, "WeChatPayInfo", "wechat_pay_info"));
        String transactionId = text(field(payInfo, "TransactionId", "transaction_id"));
        String merchantOrderNo = text(field(payInfo, "MchOrderNo", "mch_order_no"));
        String eventKey = eventKey(GOODS_DELIVER, orderNo, transactionId, merchantOrderNo);
        Map<String, Object> response = success();
        Map<String, Object> stored = storedResponse(eventKey);
        if (stored != null) return stored;
        ensureEvent(eventKey, GOODS_DELIVER, event, blankToNull(orderNo));
        if (orderNo.isBlank()) return fail(eventKey, "发货通知缺少业务订单号");
        List<Map<String, Object>> payments = jdbc.queryForList("select id from ci_payment where order_no=? limit 1", orderNo);
        if (payments.isEmpty()) return fail(eventKey, "发货通知无法匹配本地订单");
        jdbc.update("update ci_payment set transaction_id=coalesce(nullif(?,''),transaction_id),wechat_pay_transaction_id=coalesce(nullif(?,''),wechat_pay_transaction_id),channel_order_id=coalesce(nullif(?,''),channel_order_id),updated_at=? where id=?",
                cut(transactionId, 100), cut(transactionId, 100), cut(merchantOrderNo, 100), LocalDateTime.now(), payments.getFirst().get("id"));
        completeEvent(eventKey, orderNo, response, "processed");
        return response;
    }

    Map<String, Object> iosInquiry(Map<String, Object> event) {
        String payOrderId = text(field(event, "pay_order_id", "PayOrderId"));
        String channelBill = text(field(event, "channel_bill", "ChannelBill"));
        String eventKey = eventKey(IOS_INQUIRY, payOrderId, channelBill, text(field(event, "refund_time", "RefundTime")));
        Map<String, Object> stored = storedResponse(eventKey);
        if (stored != null) return stored;
        Map<String, Object> row = findPayment(payOrderId, channelBill, "");
        int provideStatus = integer(field(event, "provide_status", "ProvideStatus"), -1);
        String productId = text(field(event, "product_id", "ProductId"));
        String refundReason = text(field(event, "refund_request_reason", "RefundRequestReason"));
        boolean productMatches = productMatches(row, productId);
        boolean delivered = row != null && "success".equals(text(row.get("order_status")))
                && "delivered".equals(text(row.get("delivery_status"))) && provideStatus == 1 && productMatches;
        String decisionReason = row == null ? "本地未找到可核验的已履约记录"
                : !productMatches ? "退款商品与本地订单商品无法完成一致性核验"
                : "本地记录未同时满足查询成功和微信确认发货";
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("result_code", delivered ? 1 : 0);
        response.put("result_info", reply(delivered ? "ios_refund_reject_result_info" : "ios_refund_approve_result_info",
                delivered ? "订单已完成服务交付，建议不予退款" : "未能确认服务已经完整交付，建议退款", row, refundReason, decisionReason, 200));
        response.put("evidence", reply(delivered ? "ios_refund_reject_evidence" : "ios_refund_approve_evidence",
                delivered ? "业务订单 {order_no} 已于 {delivered_at} 完成查询并向微信确认发货。"
                        : "{decision_reason}" + (row == null ? "。" : "，业务订单 {order_no}。"), row, refundReason, decisionReason, 1000));
        String orderNo = row == null ? null : text(row.get("order_no"));
        ensureEvent(eventKey, IOS_INQUIRY, event, orderNo);
        completeEvent(eventKey, orderNo, response, "processed");
        if (row != null && !"refunded".equals(text(row.get("refund_status")))) {
            LocalDateTime requestedAt = timestamp(field(event, "refund_time", "RefundTime"));
            jdbc.update("update ci_payment set refund_source='apple',refund_status='apple_review',refund_reason=?,refund_from_status=coalesce(refund_from_status,?),refund_requested_at=coalesce(refund_requested_at,?),refund_last_error=null,updated_at=? where id=?",
                    cut(refundReason.isBlank() ? "UNKNOWN" : refundReason, 64), row.get("order_status"),
                    requestedAt == null ? LocalDateTime.now() : requestedAt, LocalDateTime.now(), row.get("payment_id"));
        }
        return response;
    }

    Map<String, Object> refundNotify(Map<String, Object> event) {
        String wxRefundId = text(field(event, "WxRefundId", "wx_refund_id"));
        String mchRefundId = text(field(event, "MchRefundId", "mch_refund_id"));
        String eventKey = eventKey(REFUND_NOTIFY, wxRefundId, mchRefundId,
                text(field(event, "RetCode", "ret_code")), text(field(event, "RefundSuccTimestamp", "refund_succ_timestamp")));
        Map<String, Object> stored = storedResponse(eventKey);
        if (stored != null) return stored;
        ensureEvent(eventKey, REFUND_NOTIFY, event, null);
        String mchOrderId = text(field(event, "MchOrderId", "mch_order_id"));
        String wxOrderId = text(field(event, "WxOrderId", "wx_order_id"));
        String transactionId = text(field(event, "TransactionId", "transaction_id"));
        Map<String, Object> row = findPayment(mchOrderId, wxOrderId, transactionId);
        if (row == null) return fail(eventKey, "退款通知无法匹配本地订单");
        String openid = text(field(event, "OpenId", "openid"));
        if (!openid.isBlank() && !text(row.get("openid")).isBlank() && !openid.equals(text(row.get("openid"))))
            return fail(eventKey, "退款通知用户标识不匹配");
        int retCode = integer(field(event, "RetCode", "ret_code"), -1);
        LocalDateTime now = LocalDateTime.now();
        String payload;
        try { payload = cut(mapper.writeValueAsString(event), 60000); } catch (Exception e) { payload = "{}"; }
        String source = integer(row.get("wechat_order_type"), -1) == 7 ? "apple" : "wechat_notify";
        if (retCode == 0) {
            BigDecimal amount = BigDecimal.valueOf(Math.max(0, integer(field(event, "RefundFee", "refund_fee"), 0)), 2);
            LocalDateTime refundedAt = timestamp(field(event, "RefundSuccTimestamp", "refund_succ_timestamp"));
            jdbc.update("update ci_order set status='refunded',updated_at=? where id=?", now, row.get("order_id"));
            jdbc.update("update ci_payment set status='refunded',refund_status='refunded',refund_source=?,refund_order_no=coalesce(nullif(?,''),refund_order_no),refund_amount=?,refund_payload=?,refund_last_error=null,refunded_at=?,wechat_order_id=coalesce(nullif(?,''),wechat_order_id),channel_order_id=case when ?<>'' and ?<>order_no then ? else channel_order_id end,updated_at=? where id=?",
                    source, cut(mchRefundId.isBlank() ? wxRefundId : mchRefundId, 100), amount, payload,
                    refundedAt == null ? now : refundedAt, cut(wxOrderId, 100), cut(mchOrderId, 100), cut(mchOrderId, 100), cut(mchOrderId, 100), now, row.get("payment_id"));
        } else {
            String restore = "refunding".equals(text(row.get("order_status")))
                    ? Optional.ofNullable(blankToNull(text(row.get("refund_from_status")))).orElse("query_failed")
                    : text(row.get("order_status"));
            if ("refunding".equals(text(row.get("order_status"))))
                jdbc.update("update ci_order set status=?,updated_at=? where id=?", restore, now, row.get("order_id"));
            jdbc.update("update ci_payment set status='paid',refund_status='failed',refund_source=?,refund_payload=?,refund_last_error=?,updated_at=? where id=?",
                    source, payload, cut(textOrDefault(field(event, "RetMsg", "ret_msg"), "退款未获批准"), 500), now, row.get("payment_id"));
        }
        Map<String, Object> response = success();
        completeEvent(eventKey, text(row.get("order_no")), response, "processed");
        return response;
    }

    Map<String, Object> acknowledgeOther(Map<String, Object> event, String rawType) {
        String type = cut(rawType.isBlank() ? "unknown" : rawType, 64);
        String orderNo = text(field(event, "OutTradeNo", "out_trade_no", "MchOrderId", "mch_order_id", "pay_order_id", "PayOrderId"));
        String identity = text(field(event, "MsgId", "msg_id", "RequestId", "request_id", "ComplaintId", "complaint_id"));
        if (identity.isBlank()) {
            try { identity = Hashing.sha256(mapper.writeValueAsString(new TreeMap<>(event))); }
            catch (Exception e) { identity = Hashing.sha256(event.toString()); }
        }
        String key = eventKey(type, identity);
        Map<String, Object> stored = storedResponse(key);
        if (stored != null) return stored;
        ensureEvent(key, type, event, blankToNull(orderNo));
        Map<String, Object> response = success();
        completeEvent(key, blankToNull(orderNo), response, "acknowledged");
        return response;
    }

    private Map<String, Object> findPayment(String... identifiers) {
        List<String> values = Arrays.stream(identifiers).map(this::text).filter(v -> !v.isBlank()).distinct().toList();
        if (values.isEmpty()) return null;
        StringBuilder sql = new StringBuilder("select o.id order_id,o.order_no,o.status order_status,o.service_snapshot_cipher,o.updated_at,p.id payment_id,p.delivery_status,p.delivered_at,p.refund_status,p.refund_from_status,p.refund_requested_at,p.wechat_order_type,u.openid from ci_order o join ci_payment p on p.order_id=o.id join ci_user u on u.id=o.user_id where ");
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sql.append(" or ");
            sql.append("(o.order_no=? or p.order_no=? or p.transaction_id=? or p.wechat_order_id=? or p.wechat_pay_transaction_id=? or p.channel_order_id=?)");
            for (int j = 0; j < 6; j++) args.add(values.get(i));
        }
        sql.append(" limit 1");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private boolean productMatches(Map<String, Object> row, String productId) {
        if (row == null) return false;
        if (productId.isBlank()) return true;
        Map<String, Object> snapshot = crypto.decrypt(text(row.get("service_snapshot_cipher")));
        return productId.equals(text(snapshot.get("payment_product_id")));
    }

    private String reply(String key, String fallback, Map<String, Object> row, String refundReason, String reason, int max) {
        String template = settings.value(key, fallback).trim();
        if (template.isBlank()) template = fallback;
        return cut(template.replace("{order_no}", row == null ? "未匹配" : text(row.get("order_no")))
                .replace("{delivered_at}", row == null ? "未知时间" : textOrDefault(row.get("delivered_at"), text(row.get("updated_at"))))
                .replace("{refund_reason}", refundReason.isBlank() ? "UNKNOWN" : refundReason)
                .replace("{decision_reason}", reason), max);
    }

    private void ensureEvent(String key, String type, Map<String, Object> event, String orderNo) {
        if (!jdbc.queryForList("select id from ci_wechat_event where event_key=?", key).isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbc.update("insert into ci_wechat_event(event_key,event_type,order_no,request_cipher,process_status,created_at,updated_at) values(?,?,?,?,\'received\',?,?)",
                    key, type, orderNo == null ? null : cut(orderNo, 32), crypto.encrypt(event), now, now);
        } catch (DuplicateKeyException ignored) { }
    }

    private Map<String, Object> storedResponse(String key) {
        List<String> rows = jdbc.query("select response_cipher from ci_wechat_event where event_key=? and response_cipher is not null and response_cipher<>'' limit 1",
                (rs, n) -> rs.getString(1), key);
        return rows.isEmpty() ? null : crypto.decrypt(rows.getFirst());
    }

    private void completeEvent(String key, String orderNo, Map<String, Object> response, String state) {
        jdbc.update("update ci_wechat_event set order_no=?,response_cipher=?,process_status=?,last_error=null,updated_at=? where event_key=?",
                orderNo == null ? null : cut(orderNo, 32), crypto.encrypt(response), state, LocalDateTime.now(), key);
    }

    private Map<String, Object> fail(String key, String message) {
        jdbc.update("update ci_wechat_event set process_status='failed',last_error=?,updated_at=? where event_key=?", cut(message, 500), LocalDateTime.now(), key);
        throw new IllegalStateException(message);
    }

    private Map<String, Object> success() { return Map.of("ErrCode", 0, "ErrMsg", "success"); }
    private String eventKey(String type, String... parts) { return Hashing.sha256(type + "|" + String.join("|", parts)); }
    private Object field(Map<String, Object> event, String... names) {
        for (String name : names) if (event.containsKey(name)) return event.get(name);
        for (Map.Entry<String, Object> entry : event.entrySet())
            for (String name : names) if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        return null;
    }
    @SuppressWarnings("unchecked") private Map<String, Object> map(Object value) { return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(); }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String textOrDefault(Object value, String fallback) { String v = text(value); return v.isBlank() ? fallback : v; }
    private int integer(Object value, int fallback) { try { return value instanceof Number n ? n.intValue() : Integer.parseInt(text(value)); } catch (Exception e) { return fallback; } }
    private String cut(String value, int max) { if (value == null) return ""; return value.codePointCount(0, value.length()) <= max ? value : value.substring(0, value.offsetByCodePoints(0, max)); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private LocalDateTime timestamp(Object value) {
        try {
            long epoch = Long.parseLong(text(value));
            if (epoch > 9_999_999_999L) epoch /= 1000;
            long now = Instant.now().getEpochSecond();
            if (epoch < 946684800 || epoch > now + 86400) return null;
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
        } catch (Exception ignored) { return value instanceof Timestamp t ? t.toLocalDateTime() : null; }
    }
}

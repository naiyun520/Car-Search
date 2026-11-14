package cn.naiblog.carsearch.wechat;

import cn.naiblog.carsearch.config.SettingService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WechatPaymentService {
    private static final String DELIVERY_PATH="/xpay/notify_provide_goods";
    private final SettingService settings; private final ObjectMapper mapper;
    private final RestClient client;
    @Autowired
    public WechatPaymentService(SettingService settings, ObjectMapper mapper) {
        this(settings,mapper,createClient());
    }
    WechatPaymentService(SettingService settings,ObjectMapper mapper,RestClient client){
        this.settings=settings;this.mapper=mapper;this.client=client;
    }
    private static RestClient createClient(){
        HttpClient httpClient=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory requestFactory=new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl("https://api.weixin.qq.com").requestFactory(requestFactory).build();
    }

    public Map<String,Object> queryOrder(String openid, String orderNo, int env) {
        Map<String,Object> result=xpay("/xpay/query_order", Map.of("openid",openid,"env",env,"order_id",orderNo), settings.wechat().withPayEnv(env), List.of(268490002));
        if(result.get("order") instanceof Map<?,?> order){@SuppressWarnings("unchecked") Map<String,Object> value=(Map<String,Object>)order;return value;}
        if(((Number)result.getOrDefault("errcode",0)).intValue()==268490002&&String.valueOf(result.getOrDefault("errmsg","")).contains("数据不存在"))return Map.of("not_found",true);
        throw new IllegalStateException("微信支付订单不存在");
    }
    public void deliver(String orderNo, int env) {
        if(env!=0&&env!=1)throw new IllegalStateException("微信发货环境无效，订单支付环境必须为正式环境或沙箱环境");
        var config=settings.wechat().withPayEnv(env);
        if(config.appKey()==null||config.appKey().isBlank())throw new IllegalStateException("微信发货签名密钥未配置：请在后台“微信与支付”填写"+(env==0?"正式环境":"沙箱环境")+"的支付 AppKey（不是 AppSecret）");
        xpay(DELIVERY_PATH,Map.of("order_id",orderNo,"env",env),config,List.of());
    }
    public Map<String,Object> refund(String openid,String orderNo,String refundNo,int leftFee,int refundFee,String reason,int env) {
        return refund(openid, orderNo, refundNo, leftFee, refundFee, reason, "1", env);
    }
    public Map<String,Object> refund(String openid,String orderNo,String refundNo,int leftFee,int refundFee,String reason,String requestFrom,int env) {
        Map<String,Object> payload = new LinkedHashMap<>(); payload.put("openid",openid); payload.put("order_id",orderNo); payload.put("refund_order_id",refundNo);
        payload.put("left_fee",leftFee); payload.put("refund_fee",refundFee); payload.put("biz_meta","car-search:"+orderNo); payload.put("refund_reason",reason); payload.put("req_from",requestFrom); payload.put("env",env);
        return xpay("/xpay/refund_order",payload,settings.wechat().withPayEnv(env),List.of(268490004));
    }

    private Map<String,Object> xpay(String path,Object payload,SettingService.WechatConfig config,List<Integer> accepted) {
        String failure=DELIVERY_PATH.equals(path)?"微信发货接口调用失败":"微信支付接口调用失败";
        try {
            String body=mapper.writeValueAsString(payload); String signature=hmac(path+"&"+body,config.appKey());
            Map<String,Object> result=post(path,body,accessToken(false),signature);
            int code=responseCode(path,result);
            if (List.of(40001,40014,42001).contains(code)) result=post(path,body,accessToken(true),signature);
            code=responseCode(path,result);
            if(code!=0&&!accepted.contains(code)) throw new IllegalStateException(failure+"："+result.getOrDefault("errmsg",code));
            return result;
        } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(failure,e); }
    }
    private int responseCode(String path,Map<String,Object> result){
        // 发货接口原文档没有响应参数；保留其空成功响应兼容，查询及退款仍严格检查业务码。
        if(DELIVERY_PATH.equals(path)&&result.isEmpty())return 0;
        return result.get("errcode") instanceof Number n?n.intValue():-1;
    }
    private Map<String,Object> post(String path,String body,String token,String sig) throws Exception {
        // 签名与发送使用同一份 UTF-8 字节；按 JSON 字节解析，避免无 charset 响应被当作 Latin-1。
        byte[] json=client.post().uri(uri->uri.path(path).queryParam("access_token",token).queryParam("pay_sig",sig).build()).header("Content-Type","application/json").body(body.getBytes(StandardCharsets.UTF_8)).retrieve().body(byte[].class);
        if(json==null||new String(json,StandardCharsets.UTF_8).isBlank()){
            if(DELIVERY_PATH.equals(path))return Map.of("errcode",0);
            throw new IllegalStateException("微信支付接口未返回有效结果");
        }
        return mapper.readValue(json,new TypeReference<LinkedHashMap<String,Object>>(){});
    }
    private String accessToken(boolean refresh) throws Exception {
        cn.naiblog.carsearch.security.TestNetworkGuard.blockExternalWechat();
        String cached=settings.secure("wechat_access_token",""); long expiry=Long.parseLong(settings.value("wechat_access_token_expires_at","0"));
        if(!refresh&&!cached.isBlank()&&expiry>Instant.now().getEpochSecond()+120)return cached;
        var c=settings.wechat();
        @SuppressWarnings("unchecked") Map<String,Object> result=client.get().uri(uri->uri.path("/cgi-bin/token").queryParam("grant_type","client_credential").queryParam("appid",c.appId()).queryParam("secret",c.appSecret()).build()).retrieve().body(Map.class);
        String token=String.valueOf(result.getOrDefault("access_token","")); if(token.isBlank())throw new IllegalStateException("微信接口调用凭证获取失败");
        settings.saveSecure("wechat_access_token",token); settings.saveValue("wechat_access_token_expires_at",String.valueOf(Instant.now().getEpochSecond()+Math.max(300,((Number)result.getOrDefault("expires_in",7200)).longValue()))); return token;
    }
    public static String hmac(String text,String key) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){throw new IllegalStateException(e);}
    }
}

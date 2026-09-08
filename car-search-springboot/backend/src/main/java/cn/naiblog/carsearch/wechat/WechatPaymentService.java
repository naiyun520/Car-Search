package cn.naiblog.carsearch.wechat;

import cn.naiblog.carsearch.config.SettingService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WechatPaymentService {
    private final SettingService settings; private final ObjectMapper mapper;
    private final RestClient client = RestClient.builder().baseUrl("https://api.weixin.qq.com").build();
    public WechatPaymentService(SettingService settings, ObjectMapper mapper) { this.settings=settings; this.mapper=mapper; }

    public Map<String,Object> queryOrder(String openid, String orderNo, int env) {
        Map<String,Object> result=xpay("/xpay/query_order", Map.of("openid",openid,"env",env,"order_id",orderNo), settings.wechat().withPayEnv(env), List.of(268490002));
        if(result.get("order") instanceof Map<?,?> order){@SuppressWarnings("unchecked") Map<String,Object> value=(Map<String,Object>)order;return value;}
        if(((Number)result.getOrDefault("errcode",0)).intValue()==268490002&&String.valueOf(result.getOrDefault("errmsg","")).contains("数据不存在"))return Map.of("not_found",true);
        throw new IllegalStateException("微信支付订单不存在");
    }
    public void deliver(String orderNo, int env) {
        postWithoutPaySignature("/xpay/notify_provide_goods",Map.of("order_id",orderNo,"env",env));
    }
    public Map<String,Object> refund(String openid,String orderNo,String refundNo,int leftFee,int refundFee,String reason,int env) {
        Map<String,Object> payload = new LinkedHashMap<>(); payload.put("openid",openid); payload.put("order_id",orderNo); payload.put("refund_order_id",refundNo);
        payload.put("left_fee",leftFee); payload.put("refund_fee",refundFee); payload.put("biz_meta","car-admin:"+orderNo); payload.put("refund_reason",reason); payload.put("req_from","1"); payload.put("env",env);
        return xpay("/xpay/refund_order",payload,settings.wechat().withPayEnv(env),List.of(268490004));
    }

    private Map<String,Object> xpay(String path,Object payload,SettingService.WechatConfig config,List<Integer> accepted) {
        try {
            String body=mapper.writeValueAsString(payload); String signature=hmac(path+"&"+body,config.appKey());
            Map<String,Object> result=post(path,body,accessToken(false),signature);
            int code=((Number)result.getOrDefault("errcode",-1)).intValue();
            if (List.of(40001,40014,42001).contains(code)) result=post(path,body,accessToken(true),signature);
            code=((Number)result.getOrDefault("errcode",-1)).intValue();
            if(code!=0&&!accepted.contains(code)) throw new IllegalStateException("微信支付接口调用失败："+result.getOrDefault("errmsg",code));
            return result;
        } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException("微信支付接口调用失败",e); }
    }
    private Map<String,Object> post(String path,String body,String token,String sig) throws Exception {
        String json=client.post().uri(uri->uri.path(path).queryParam("access_token",token).queryParam("pay_sig",sig).build()).header("Content-Type","application/json").body(body).retrieve().body(String.class);
        return mapper.readValue(json,new TypeReference<LinkedHashMap<String,Object>>(){});
    }
    private void postWithoutPaySignature(String path,Object payload) {
        try {
            String body=mapper.writeValueAsString(payload);
            Map<String,Object> result=postUnsigned(path,body,accessToken(false));
            int code=((Number)result.getOrDefault("errcode",0)).intValue();
            if(List.of(40001,40014,42001).contains(code))result=postUnsigned(path,body,accessToken(true));
            code=((Number)result.getOrDefault("errcode",0)).intValue();
            if(code!=0)throw new IllegalStateException("微信发货接口调用失败："+result.getOrDefault("errmsg",code));
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("微信发货接口调用失败",e);}
    }
    private Map<String,Object> postUnsigned(String path,String body,String token)throws Exception{
        String json=client.post().uri(uri->uri.path(path).queryParam("access_token",token).build()).header("Content-Type","application/json").body(body).retrieve().body(String.class);
        if(json==null||json.isBlank())return Map.of("errcode",0);
        return mapper.readValue(json,new TypeReference<LinkedHashMap<String,Object>>(){});
    }
    private String accessToken(boolean refresh) throws Exception {
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

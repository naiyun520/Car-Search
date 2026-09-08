package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.wechat.WechatPaymentService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class VirtualPaymentService {
    private final SettingService settings; private final CryptoService crypto; private final ObjectMapper mapper;
    public VirtualPaymentService(SettingService settings,CryptoService crypto,ObjectMapper mapper){this.settings=settings;this.crypto=crypto;this.mapper=mapper;}
    public SettingService.WechatConfig configured(){var c=settings.wechat();if(c.appId().isBlank()||c.offerId().isBlank()||c.appKey().isBlank())throw new IllegalStateException("虚拟支付参数尚未配置，请先填写 OfferID 与当前支付环境对应的 AppKey");return c;}
    public SettingService.WechatConfig ensureAvailable(){if(!"1".equals(settings.value("payment_enabled","0")))throw new IllegalStateException("支付通道维护中，请稍后再试");return configured();}
    public Map<String,Object> clientParams(Map<String,Object> order,Map<String,Object> user,Map<String,Object> service){
        var c=ensureAvailable();String product=String.valueOf(service.getOrDefault("payment_product_id","")).trim();if(product.isBlank())throw new IllegalStateException("该服务尚未配置微信支付道具ID");
        String session=String.valueOf(crypto.decrypt(String.valueOf(user.getOrDefault("session_key_cipher",""))).getOrDefault("value",""));if(session.isBlank())throw new IllegalStateException("微信登录态已失效，请重新进入小程序");
        int cents=new BigDecimal(String.valueOf(order.get("amount"))).movePointRight(2).intValueExact();if(cents<100)throw new IllegalStateException("虚拟支付服务售价不能低于1元，请联系管理员调整服务及微信道具价格");
        try { Map<String,Object> sign=new LinkedHashMap<>();sign.put("offerId",c.offerId());sign.put("buyQuantity",1);sign.put("env",c.payEnv());sign.put("currencyType","CNY");sign.put("productId",product);sign.put("goodsPrice",cents);sign.put("outTradeNo",order.get("order_no"));sign.put("attach",order.get("order_no"));
            String encoded=mapper.writeValueAsString(sign);return Map.of("signData",encoded,"paySig",WechatPaymentService.hmac("requestVirtualPayment&"+encoded,c.appKey()),"signature",WechatPaymentService.hmac(encoded,session),"mode","short_series_goods");
        }catch(Exception e){throw new IllegalStateException("支付参数生成失败",e);}
    }
}

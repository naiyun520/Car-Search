package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import cn.naiblog.carsearch.security.CryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VirtualPaymentServiceTest {
    private SettingService settings;
    private CryptoService crypto;
    private ObjectMapper mapper;
    private VirtualPaymentService payment;

    @BeforeEach
    void setUp() {
        settings=mock(SettingService.class);crypto=mock(CryptoService.class);mapper=new ObjectMapper();
        when(settings.value("payment_enabled","0")).thenReturn("1");
        when(settings.wechat()).thenReturn(new SettingService.WechatConfig("wx123","secret","offer-1","sandbox-key","production-key",0));
        when(crypto.decrypt(anyString())).thenReturn(Map.of("value","session-key"));
        payment=new VirtualPaymentService(settings,crypto,mapper);
    }

    @Test
    void iosOrderUsesIosProductAndPrice() throws Exception {
        Map<String,Object> service=Map.of("payment_product_id_android","android-product","payment_product_id_ios","ios-product","sale_price_android",new BigDecimal("9.90"),"sale_price_ios",new BigDecimal("12.80"));
        Map<String,Object> order=Map.of("order_no","ORDER12345678","amount",payment.priceFor(service,"ios"),"client_platform","ios");
        Map<String,Object> params=payment.clientParams(order,Map.of("session_key_cipher","cipher"),service);
        Map<?,?> sign=mapper.readValue(String.valueOf(params.get("signData")),Map.class);
        assertEquals("ios-product",sign.get("productId"));
        assertEquals(1280,((Number)sign.get("goodsPrice")).intValue());
    }

    @Test
    void nonIosOrderUsesAndroidProductAndPrice() throws Exception {
        Map<String,Object> service=Map.of("payment_product_id_android","android-product","payment_product_id_ios","ios-product","sale_price_android",new BigDecimal("9.90"),"sale_price_ios",new BigDecimal("12.80"));
        Map<String,Object> order=Map.of("order_no","ORDER12345679","amount",payment.priceFor(service,"android"),"client_platform","android");
        Map<String,Object> params=payment.clientParams(order,Map.of("session_key_cipher","cipher"),service);
        Map<?,?> sign=mapper.readValue(String.valueOf(params.get("signData")),Map.class);
        assertEquals("android-product",sign.get("productId"));
        assertEquals(990,((Number)sign.get("goodsPrice")).intValue());
    }
}

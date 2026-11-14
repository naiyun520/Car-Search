package cn.naiblog.carsearch.wechat;

import cn.naiblog.carsearch.config.SettingService;
import cn.naiblog.carsearch.security.TestNetworkGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** HTTP 出口全部由 MockRestServiceServer 接管，绝不发送真实微信请求。 */
class WechatPaymentHttpContractTest {
    private static final String PRODUCTION="LOCAL-PRODUCTION-PAY-KEY",SANDBOX="LOCAL-SANDBOX-PAY-KEY";
    private final ObjectMapper mapper=new ObjectMapper();
    private SettingService settings;
    private MockRestServiceServer server;
    private WechatPaymentService service;
    private MockedStatic<TestNetworkGuard> networkGuard;
    @BeforeEach void setup(){
        settings=mock(SettingService.class);
        when(settings.wechat()).thenReturn(config(1));
        when(settings.secure("wechat_access_token","")).thenReturn("LOCAL-CACHED-TOKEN");
        when(settings.value("wechat_access_token_expires_at","0")).thenReturn("9999999999");
        var builder=RestClient.builder().baseUrl("https://api.weixin.qq.com");
        server=MockRestServiceServer.bindTo(builder).build();
        service=new WechatPaymentService(settings,mapper,builder.build());
        // 只解除本单元测试的入口保护；以上模拟传输会拒绝任何未预期请求，绝不回落到网络。
        networkGuard=mockStatic(TestNetworkGuard.class);
    }
    @AfterEach void verifyRequests(){try{server.verify();}finally{networkGuard.close();}}
    private SettingService.WechatConfig config(int env){return new SettingService.WechatConfig("LOCAL-APP","LOCAL-APP-SECRET","LOCAL-OFFER",SANDBOX,PRODUCTION,env);}
    private RequestMatcher signed(String path,int env,String token){
        return request->{
            assertEquals(HttpMethod.POST,request.getMethod());assertEquals(path,request.getURI().getPath());
            var query=UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
            assertEquals(token,query.getFirst("access_token"));assertNotNull(query.getFirst("pay_sig"));
            byte[] bytes=((MockClientHttpRequest)request).getBodyAsBytes();
            @SuppressWarnings("unchecked") Map<String,Object> body=mapper.readValue(bytes,Map.class);
            assertEquals(env,((Number)body.get("env")).intValue());assertEquals("LOCAL-ORDER",body.get("order_id"));
            try{
                Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec((env==1?SANDBOX:PRODUCTION).getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
                String expected=HexFormat.of().formatHex(mac.doFinal((path+"&"+new String(bytes,StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)));
                assertEquals(expected,query.getFirst("pay_sig"),"签名必须匹配实际发送的原始请求体和订单所属环境");
            }catch(java.security.GeneralSecurityException e){throw new AssertionError(e);}
        };
    }
    @Test void productionDeliveryUsesOrderEnvironmentDespiteCurrentSandboxSetting(){
        server.expect(signed("/xpay/notify_provide_goods",0,"LOCAL-CACHED-TOKEN")).andRespond(withSuccess("{\"errcode\":0}",MediaType.APPLICATION_JSON));
        assertDoesNotThrow(()->service.deliver("LOCAL-ORDER",0));
    }
    @Test void sandboxDeliveryUsesSandboxPaymentKey(){
        when(settings.wechat()).thenReturn(config(0));
        server.expect(signed("/xpay/notify_provide_goods",1,"LOCAL-CACHED-TOKEN")).andRespond(withSuccess("{\"errcode\":0}",MediaType.APPLICATION_JSON));
        assertDoesNotThrow(()->service.deliver("LOCAL-ORDER",1));
    }
    @Test void tokenRefreshRetriesWithPaymentSignatureStillPresent(){
        server.expect(signed("/xpay/notify_provide_goods",0,"LOCAL-CACHED-TOKEN")).andRespond(withSuccess("{\"errcode\":40001}",MediaType.APPLICATION_JSON));
        expectTokenRefresh();
        server.expect(signed("/xpay/notify_provide_goods",0,"LOCAL-NEW-TOKEN")).andRespond(withSuccess("{\"errcode\":0}",MediaType.APPLICATION_JSON));
        service.deliver("LOCAL-ORDER",0);
        verify(settings).saveSecure("wechat_access_token","LOCAL-NEW-TOKEN");
    }
    private void expectTokenRefresh(){
        server.expect(request->{assertEquals(HttpMethod.GET,request.getMethod());assertEquals("/cgi-bin/token",request.getURI().getPath());})
                .andRespond(withSuccess("{\"access_token\":\"LOCAL-NEW-TOKEN\",\"expires_in\":7200}",MediaType.APPLICATION_JSON));
    }
    @Test void repeatedTokenFailureIsNotTreatedAsSuccessfulDelivery(){
        server.expect(signed("/xpay/notify_provide_goods",0,"LOCAL-CACHED-TOKEN")).andRespond(withSuccess("{\"errcode\":40001}",MediaType.APPLICATION_JSON));
        expectTokenRefresh();
        server.expect(signed("/xpay/notify_provide_goods",0,"LOCAL-NEW-TOKEN")).andRespond(withSuccess("{\"errcode\":40001}",MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class,()->service.deliver("LOCAL-ORDER",0));
    }
    @Test void utf8ErrorWithoutCharsetIsReadableAndRemainsFailure(){
        String message="签名字段[pay_sig]为空 rid: LOCAL-ONLY";
        byte[] response=("{\"errcode\":268490001,\"errmsg\":\""+message+"\"}").getBytes(StandardCharsets.UTF_8);
        server.expect(signed("/xpay/notify_provide_goods",0,"LOCAL-CACHED-TOKEN")).andRespond(withSuccess(response,MediaType.TEXT_PLAIN));
        var error=assertThrows(IllegalStateException.class,()->service.deliver("LOCAL-ORDER",0));
        assertEquals("微信发货接口调用失败："+message,error.getMessage());
    }
    @ParameterizedTest @ValueSource(strings={"","{}"," \n"}) void deliveryRetainsDocumentedEmptySuccessResponseCompatibility(String body){
        server.expect(signed("/xpay/notify_provide_goods",0,"LOCAL-CACHED-TOKEN")).andRespond(withSuccess(body.getBytes(StandardCharsets.UTF_8),MediaType.APPLICATION_JSON));
        assertDoesNotThrow(()->service.deliver("LOCAL-ORDER",0));
    }
    @Test void emptyOrderQueryResponseCannotConfirmPayment(){
        server.expect(signed("/xpay/query_order",0,"LOCAL-CACHED-TOKEN")).andRespond(withSuccess(new byte[0],MediaType.APPLICATION_JSON));
        assertThrows(IllegalStateException.class,()->service.queryOrder("LOCAL-USER","LOCAL-ORDER",0));
    }
    @Test void missingPaymentKeyStopsBeforeSending(){
        when(settings.wechat()).thenReturn(new SettingService.WechatConfig("LOCAL-APP","LOCAL-APP-SECRET","LOCAL-OFFER",SANDBOX,"",0));
        var error=assertThrows(IllegalStateException.class,()->service.deliver("LOCAL-ORDER",0));
        assertTrue(error.getMessage().contains("正式环境"));assertTrue(error.getMessage().contains("AppKey"));
        verify(settings,never()).secure("wechat_access_token","");
    }
    @Test void invalidEnvironmentCannotUseTheProductionKeyByDefault(){
        assertThrows(IllegalStateException.class,()->service.deliver("LOCAL-ORDER",2));
        verify(settings,never()).secure("wechat_access_token","");
    }
    @Test void queryOrderRetainsItsSignedTransport(){
        server.expect(signed("/xpay/query_order",0,"LOCAL-CACHED-TOKEN")).andRespond(withSuccess("{\"errcode\":0,\"order\":{\"status\":2,\"left_fee\":990}}",MediaType.APPLICATION_JSON));
        assertEquals(2,((Number)service.queryOrder("LOCAL-USER","LOCAL-ORDER",0).get("status")).intValue());
    }
    @Test void refundRetainsItsSignedTransportAndAmount(){
        server.expect(signed("/xpay/refund_order",1,"LOCAL-CACHED-TOKEN")).andExpect(request->{
            @SuppressWarnings("unchecked") Map<String,Object> body=mapper.readValue(((MockClientHttpRequest)request).getBodyAsBytes(),Map.class);
            assertEquals(990,((Number)body.get("refund_fee")).intValue());assertEquals("LOCAL-REFUND",body.get("refund_order_id"));
        }).andRespond(withSuccess("{\"errcode\":0,\"refund_order_id\":\"LOCAL-REFUND\"}",MediaType.APPLICATION_JSON));
        assertEquals("LOCAL-REFUND",service.refund("LOCAL-USER","LOCAL-ORDER","LOCAL-REFUND",990,990,"2",1).get("refund_order_id"));
    }
}

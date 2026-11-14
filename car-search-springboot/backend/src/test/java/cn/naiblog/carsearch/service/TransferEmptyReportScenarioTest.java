package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import cn.naiblog.carsearch.security.CryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Hypothetical report processing only. No HTTP, database connection, payment or supplier call. */
class TransferEmptyReportScenarioTest {
    @Test void successfulEmptyTransferArrayRemainsSuccessAndDisplaysConfirmedMeaning() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        Map<String,Object> payload=mapper.readValue("""
            {"version":"1.0.0","object":"Car/transferReport","message":"","number":200,
             "result":{"order_status":1,"data":[]}}
            """,Map.class);
        Map<String,Object> report=ProviderService.flattenReport((Map<String,Object>)payload.get("result"));
        String schema="""
            {"order_status":"订单状态","data[]":"过户记录","data[].change_month":"过户年月",
             "data[].old_plate":"过户前车牌","data[].new_plate":"过户后车牌"}
            """;
        assertEquals(QueryStatusResolver.SUCCESS,QueryStatusResolver.resolve(report));
        assertTrue(QueryStatusResolver.reportHasContent(mapper.readValue(schema,Map.class),report));
        ProviderService provider=new ProviderService(null,mapper,null);
        List<Map<String,Object>> items=provider.normalize(Map.of("code","transfer_order","fetch_api_path","/Car/transferReport","result_schema",schema),report);
        assertEquals("查询成功",items.get(0).get("value"));
        assertEquals("该车辆目前暂未出现过户记录",items.get(1).get("value"));
        assertEquals("过户记录",items.get(1).get("label"));
        assertEquals("array",items.get(1).get("display"));

        CryptoService crypto=mock(CryptoService.class);
        when(crypto.decrypt("MOCK_RESULT")).thenReturn(Map.of("items",items));
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        SettingService settings=mock(SettingService.class);
        PaymentDeliveryService delivery=mock(PaymentDeliveryService.class);
        CheckoutService checkout=new CheckoutService(jdbc,crypto,mapper,mock(VirtualPaymentService.class),
            mock(QueryRunnerService.class),mock(PaymentReconciliationService.class),delivery,settings);
        Map<String,Object> order=new LinkedHashMap<>();
        order.put("id",1L);order.put("status","success");order.put("order_no","LOCAL-TRANSFER-EMPTY");
        order.put("service_name","过户信息查询");order.put("queried_at",LocalDateTime.now());
        order.put("result_cipher","MOCK_RESULT");
        Map<String,Object> detail=checkout.detail(order);
        assertEquals("success",detail.get("status"));
        assertEquals(2,((List<?>)detail.get("result")).size());
        assertFalse(detail.containsKey("auto_refund_enabled"));
        assertFalse(detail.containsKey("refund_status"));
        verifyNoInteractions(jdbc,settings);
        System.out.println("LOCAL TRANSFER SCENARIO: "+mapper.writeValueAsString(detail));
    }

    private List<Map<String,Object>> normalize(Map<String,Object> service,Map<String,Object> report)throws Exception{
        return new ProviderService(null,new ObjectMapper(),null).normalize(service,report);
    }
    @Test void otherBusinessesEmptyArraysKeepTheirOriginalMeaning()throws Exception{
        var items=normalize(Map.of("fetch_api_path","/CarMaintain/fetchReport","result_schema","{\"data[]\":\"维修记录\"}"),Map.of("order_status",1,"data",List.of()));
        assertEquals("维修记录",items.getFirst().get("label"));assertEquals("暂无数据",items.getFirst().get("value"));
    }
    @Test void missingNullAndNonArrayTransferDataAreNotTreatedAsNoRecord()throws Exception{
        Map<String,Object> service=Map.of("fetch_api_path","/Car/transferReport","result_schema","{\"data[]\":\"过户记录\"}");
        for(Object value:List.of("",Map.of())){
            var item=normalize(service,Map.of("order_status",1,"data",value)).getFirst();
            assertNotEquals("该车辆目前暂未出现过户记录",item.get("value"));
        }
        assertEquals("暂无数据",normalize(service,Map.of("order_status",1)).getFirst().get("value"));
        Map<String,Object> nullReport=new HashMap<>();nullReport.put("order_status",1);nullReport.put("data",null);
        assertEquals("暂无数据",normalize(service,nullReport).getFirst().get("value"));
    }
    @Test void runningFailedAndUnknownStatusesDoNotClaimNoTransferRecords()throws Exception{
        var service=Map.<String,Object>of("fetch_api_path","/Car/transferReport","result_schema","{\"data[]\":\"过户记录\"}");
        for(int status:List.of(0,2,9))assertEquals("暂无数据",normalize(service,Map.of("order_status",status,"data",List.of())).getFirst().get("value"));
    }
    @Test void nonEmptyTransferRecordsAreUnchanged()throws Exception{
        var items=normalize(Map.of("fetch_api_path","/Car/transferReport","result_schema","{\"data[]\":\"过户记录\",\"data[].change_month\":\"过户年月\"}"),Map.of("order_status",1,"data",List.of(Map.of("change_month","202606"))));
        assertTrue(items.getFirst().get("value") instanceof List<?>);
        assertEquals("2026-06",((Map<?,?>)((List<?>)items.getFirst().get("value")).getFirst()).get("change_month"));
    }
    @Test void submitServiceAndInferredGroupReceiveSameText()throws Exception{
        var items=normalize(Map.of("api_path","Car/transferOrder","result_schema","{\"data[].change_month\":\"过户年月\"}"),Map.of("order_status","1","data",List.of()));
        assertEquals("过户记录",items.getFirst().get("label"));
        assertEquals("该车辆目前暂未出现过户记录",items.getFirst().get("value"));
    }
}

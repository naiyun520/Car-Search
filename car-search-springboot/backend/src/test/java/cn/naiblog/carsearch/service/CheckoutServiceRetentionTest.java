package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import cn.naiblog.carsearch.security.CryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 结果保留期：一级查询结果生成后保留 24 小时，二级查询长期可查看 */
class CheckoutServiceRetentionTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final CryptoService crypto=mock(CryptoService.class);
    private final CheckoutService service=new CheckoutService(jdbc,crypto,mock(ObjectMapper.class),
            mock(VirtualPaymentService.class),mock(QueryRunnerService.class),mock(PaymentReconciliationService.class),
            mock(PaymentDeliveryService.class),mock(SettingService.class));

    private void stubCrypto(){
        when(crypto.decrypt("RESULT")).thenReturn(Map.of("items",List.of(Map.of("key","result.vin","label","车架号","value","LVSHCAMB0NE123456"))));
        when(crypto.decrypt("SNAP_SINGLE")).thenReturn(Map.of("query_mode","single","callback_mode","none"));
        when(crypto.decrypt("SNAP_ASYNC")).thenReturn(Map.of("query_mode","async","callback_mode","callback"));
    }
    private Map<String,Object> order(String snapshot,LocalDateTime queriedAt){
        Map<String,Object> o=new LinkedHashMap<>();
        o.put("order_no","ORDER-RETENTION-1");
        o.put("service_name","测试服务");
        o.put("status","success");
        o.put("result_cipher","RESULT");
        o.put("service_snapshot_cipher",snapshot);
        o.put("service_id",1);
        o.put("queried_at",Timestamp.valueOf(queriedAt));
        o.put("created_at",Timestamp.valueOf(queriedAt));
        return o;
    }

    @Test
    void singleQueryWithin24HoursReturnsResultAndRemainingSeconds(){
        stubCrypto();
        Map<String,Object> r=service.detail(order("SNAP_SINGLE",LocalDateTime.now().minusHours(23)));
        assertEquals("limited",r.get("result_retention"));
        assertNotNull(r.get("result"));
        assertNotNull(r.get("result_expires_at"));
        assertNull(r.get("result_expired"));
        long remaining=((Number)r.get("result_remaining_seconds")).longValue();
        assertTrue(remaining>3300&&remaining<=3600,"剩余秒数应接近 1 小时，实际="+remaining);
    }

    @Test
    void singleQueryAfter24HoursIsExpiredWithoutResult(){
        stubCrypto();
        Map<String,Object> r=service.detail(order("SNAP_SINGLE",LocalDateTime.now().minusHours(25)));
        assertEquals(Boolean.TRUE,r.get("result_expired"));
        assertEquals(Boolean.TRUE,r.get("result_destroyed"),"旧版小程序依赖 result_destroyed 兜底提示");
        assertFalse(r.containsKey("result"));
        assertNotNull(r.get("result_expired_at"));
        assertEquals(0L,((Number)r.get("result_remaining_seconds")).longValue());
    }

    @Test
    void viewedOrderStaysVisibleBeforeExpiry(){
        stubCrypto();
        Map<String,Object> o=order("SNAP_SINGLE",LocalDateTime.now().minusHours(1));
        o.put("result_viewed_at",Timestamp.valueOf(LocalDateTime.now().minusMinutes(30)));
        Map<String,Object> r=service.detail(o);
        assertNotNull(r.get("result"),"已查看过不再立即销毁，24 小时内仍可查看");
        assertNull(r.get("result_expired"));
    }

    @Test
    void clearedCipherShowsExpiredPlaceholder(){
        stubCrypto();
        Map<String,Object> o=order("SNAP_SINGLE",LocalDateTime.now().minusHours(2));
        o.remove("result_cipher");
        Map<String,Object> r=service.detail(o);
        assertEquals(Boolean.TRUE,r.get("result_expired"));
        assertEquals(Boolean.TRUE,r.get("result_destroyed"));
        assertNotNull(r.get("message"));
        assertFalse(r.containsKey("result"));
    }

    @Test
    void asyncQueryNeverExpires(){
        stubCrypto();
        Map<String,Object> r=service.detail(order("SNAP_ASYNC",LocalDateTime.now().minusDays(10)));
        assertEquals("permanent",r.get("result_retention"));
        assertNotNull(r.get("result"));
        assertNull(r.get("result_expires_at"));
        assertNull(r.get("result_expired"));
    }
}

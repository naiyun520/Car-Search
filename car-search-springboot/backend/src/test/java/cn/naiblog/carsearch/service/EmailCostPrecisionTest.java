package cn.naiblog.carsearch.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EmailCostPrecisionTest {
    @Test void smallConfiguredCostsAndWalletFractionsAreNotPrintedAsZero(){
        var db=mock(JdbcTemplate.class);
        when(db.queryForList(anyString())).thenReturn(List.of(Map.of("name","汽修宝","code","qxb","status","success","balance",new BigDecimal("1.2345"),"observed_at","2026-11-04 12:00:00")));
        var service=new EmailNotificationService(db,null,null,null);
        String body=ReflectionTestUtils.invokeMethod(service,"body",Map.of("amount",new BigDecimal("9.90"),"cost_amount",new BigDecimal("0.0431")),"安卓","query_success",null,null,null,null);
        assertTrue(body.contains("0.0431"));assertTrue(body.contains("1.2345"));assertTrue(body.contains("估算"));assertTrue(body.contains("采集时间"));
        verify(db).queryForList(contains("p.balance_mode='independent_api'"));
    }
}

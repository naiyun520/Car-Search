package cn.naiblog.carsearch.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProviderBalanceCapabilityTest {
    @Test void unsupportedProviderNeverRequestsBalanceEvenIfEnabledWasSetManually(){
        var db=mock(JdbcTemplate.class);var provider=mock(ProviderService.class);
        when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("id",2,"status",1,"balance_enabled",1,"balance_mode","unsupported")));
        var result=new ProviderBalanceService(db,provider).refreshNow(2);
        assertEquals("unsupported",result.get("status"));assertTrue(result.get("message").toString().contains("未配置余额接口"));verifyNoInteractions(provider);verify(db,times(1)).queryForList(anyString(),any(Object[].class));
    }
    @Test void disabledProviderDoesNotReuseAnOldBalanceOrMakeANetworkCall(){
        var db=mock(JdbcTemplate.class);var provider=mock(ProviderService.class);
        when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("id",1,"status",0,"balance_enabled",1,"balance_mode","independent_api")));
        assertEquals("disabled",new ProviderBalanceService(db,provider).refreshNow(1).get("status"));verifyNoInteractions(provider);
    }
    @Test void eligibleProviderStoresItsOwnSnapshot()throws Exception{
        var db=mock(JdbcTemplate.class);var provider=mock(ProviderService.class);
        Map<String,Object> row=Map.of("id",1,"status",1,"balance_enabled",1,"balance_mode","independent_api");
        when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row),List.of());
        when(provider.queryBalance(row)).thenReturn(new ProviderService.BalanceResult(new BigDecimal("123.45"),"200"));
        var result=new ProviderBalanceService(db,provider).refreshNow(1);
        assertEquals("success",result.get("status"));assertEquals(new BigDecimal("123.45"),result.get("balance"));verify(provider,times(1)).queryBalance(row);verify(db).update(contains("ci_provider_balance_snapshot"),any(Object[].class));
    }
    @Test void orderTrackingStoresBeforeAndAfterSnapshots()throws Exception{
        var db=mock(JdbcTemplate.class);var provider=mock(ProviderService.class);
        Map<String,Object> row=Map.of("id",1,"status",1,"balance_enabled",1,"balance_mode","independent_api","order_balance_tracking_enabled",1);
        when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(row));
        when(provider.queryBalance(row)).thenReturn(new ProviderService.BalanceResult(new BigDecimal("100.00"),"200"),new ProviderService.BalanceResult(new BigDecimal("98.50"),"200"));
        var balances=new ProviderBalanceService(db,provider);
        assertEquals("success",balances.captureOrderBalance(9L,12L,1,"before").get("status"));
        assertEquals("success",balances.captureOrderBalance(9L,12L,1,"after").get("status"));
        verify(provider,times(2)).queryBalance(row);
        verify(db,times(2)).update(contains("provider_call_id"),any(Object[].class));
    }
    @Test void disabledOrderTrackingNeverAddsBalanceTraffic()throws Exception{
        var db=mock(JdbcTemplate.class);var provider=mock(ProviderService.class);
        when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("id",1,"status",1,"balance_enabled",1,"balance_mode","independent_api","order_balance_tracking_enabled",0)));
        assertEquals("tracking_disabled",new ProviderBalanceService(db,provider).captureOrderBalance(9L,12L,1,"before").get("status"));
        verifyNoInteractions(provider);
    }
}

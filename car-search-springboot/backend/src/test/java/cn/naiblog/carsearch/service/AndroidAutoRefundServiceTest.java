package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AndroidAutoRefundServiceTest {
    @Test
    void disabledDoesNotScanOrders() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        SettingService settings=mock(SettingService.class);
        PaymentLifecycleService lifecycle=mock(PaymentLifecycleService.class);
        when(settings.value("android_auto_refund_query_failed","0")).thenReturn("0");

        assertEquals(0,new AndroidAutoRefundService(jdbc,settings,lifecycle).processPending(50));
        verifyNoInteractions(jdbc,lifecycle);
    }

    @Test
    void enabledStartsEligibleAndroidRefund() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        SettingService settings=mock(SettingService.class);
        PaymentLifecycleService lifecycle=mock(PaymentLifecycleService.class);
        when(settings.value("android_auto_refund_query_failed","0")).thenReturn("1");
        when(settings.value(eq("android_auto_refund_enabled_at"),anyString())).thenReturn("2026-09-22T00:00:00");
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("order_no","ORDER-ANDROID-1")));
        when(lifecycle.requestAutomaticAndroidRefund("ORDER-ANDROID-1")).thenReturn("processing");

        assertEquals(1,new AndroidAutoRefundService(jdbc,settings,lifecycle).processPending(50));
        verify(lifecycle).requestAutomaticAndroidRefund("ORDER-ANDROID-1");
    }
}

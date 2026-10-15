package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.SettingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailNotificationServiceTest {
    @Test
    void balanceFailureWithEmptyProviderCodeUsesDedicatedEventAndDoesNotDuplicateGenericFailure() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        SettingService settings=mock(SettingService.class);
        MailService mail=mock(MailService.class);
        Executor executor=mock(Executor.class);
        when(settings.value("email_notify_enabled","0")).thenReturn("1");
        when(settings.value("email_event_provider_balance","0")).thenReturn("1");
        when(mail.recipients()).thenReturn(List.of("ops@example.com"));
        when(mail.layout(anyString(),anyString())).thenAnswer(i->i.getArgument(1));
        when(jdbc.queryForList(contains("from ci_order"),eq(9L))).thenReturn(List.of(Map.ofEntries(
                Map.entry("order_id",9L),Map.entry("order_no","O20260925001"),Map.entry("user_id",12L),
                Map.entry("service_name","过户次数"),Map.entry("amount",new BigDecimal("9.90")),
                Map.entry("client_platform","android"),Map.entry("query_attempts",2),Map.entry("payment_id",5L),
                Map.entry("wechat_order_type",0),Map.entry("refund_attempts",0),
                Map.entry("query_last_error","供应商查询未成功：余额不足"))));

        new EmailNotificationService(jdbc,settings,mail,executor)
                .queryFailed(9L,"","RID-1","供应商查询未成功：余额不足");

        ArgumentCaptor<Object[]> args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(contains("insert ignore into ci_email_delivery"),args.capture());
        assertEquals("provider_balance:9:2",args.getValue()[0]);
        assertEquals(EmailNotificationService.PROVIDER_BALANCE,args.getValue()[1]);
        assertTrue(String.valueOf(args.getValue()[4]).contains("安卓"));
        assertTrue(String.valueOf(args.getValue()[5]).contains("余额不足"));
    }

    @Test
    void masterSwitchOffDoesNotReadOrderOrQueueMail() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        SettingService settings=mock(SettingService.class);
        when(settings.value("email_notify_enabled","0")).thenReturn("0");
        EmailNotificationService service=new EmailNotificationService(jdbc,settings,mock(MailService.class),mock(Executor.class));

        service.paymentSucceeded(1L);

        verify(jdbc,never()).queryForList(anyString(),any(Object[].class));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
}

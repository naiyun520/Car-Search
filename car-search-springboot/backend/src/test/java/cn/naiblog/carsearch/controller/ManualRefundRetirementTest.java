package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.service.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ManualRefundRetirementTest {
    JdbcTemplate jdbc;PaymentLifecycleService lifecycle;PaymentReconciliationService payments;
    EmailNotificationService email;MockMvc mvc;
    @BeforeEach void setup(){
        jdbc=mock(JdbcTemplate.class);lifecycle=mock(PaymentLifecycleService.class);
        payments=mock(PaymentReconciliationService.class);email=mock(EmailNotificationService.class);
        var controller=new AdminOrderController(jdbc,mock(CryptoService.class),new ObjectMapper(),payments,lifecycle,
            mock(QueryRunnerService.class),mock(QueryJobService.class),mock(AuditService.class),email);
        mvc=MockMvcBuilders.standaloneSetup(controller).build();
    }
    @Test void removedRegistrationEndpointCannotMarkAnOrderRefunded()throws Exception{
        mvc.perform(post("/admin-api/orders/ORDER/manual-refund").contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(jdbc,lifecycle,payments,email);
    }
    @Test void actualRefundEndpointStillUsesVerifiedRefundLifecycle()throws Exception{
        when(lifecycle.requestRefund("ORDER","2")).thenReturn("processing");
        mvc.perform(post("/admin-api/orders/ORDER/refund").contentType("application/json").content("{\"reason\":\"2\"}"))
            .andExpect(status().isOk());
        verify(lifecycle).requestRefund("ORDER","2");verifyNoInteractions(jdbc,payments,email);
    }
    @Test void refundStatusCheckRemainsAvailable()throws Exception{
        when(lifecycle.reconcileRefund("ORDER")).thenReturn("refunded");
        mvc.perform(post("/admin-api/orders/ORDER/refund-status")).andExpect(status().isOk());
        verify(lifecycle).reconcileRefund("ORDER");verifyNoInteractions(jdbc,payments,email);
    }
}

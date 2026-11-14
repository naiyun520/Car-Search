package cn.naiblog.carsearch.service;
import cn.naiblog.carsearch.config.SettingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MaintenanceSchedulerTest {
 @Test void maintenanceNoLongerHasIndependentQueryTimeoutWriter(){
   assertFalse(Arrays.stream(MaintenanceScheduler.class.getDeclaredMethods()).anyMatch(m->m.getName().equals("failStaleQueries")));
 }
 @Test void maintenanceStillReconcilesPaymentsAndRefunds(){
   var lifecycle=mock(PaymentLifecycleService.class);when(lifecycle.reconcilePending(50)).thenReturn(java.util.Map.of());
   var refund=mock(AndroidAutoRefundService.class);
   var service=new MaintenanceScheduler(mock(JdbcTemplate.class),lifecycle,refund,mock(SettingService.class),mock(EmailNotificationService.class));
   service.reconcile();verify(lifecycle).reconcilePending(50);verify(refund).processPending(50);
 }
}

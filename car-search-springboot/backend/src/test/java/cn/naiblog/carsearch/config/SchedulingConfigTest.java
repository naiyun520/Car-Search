package cn.naiblog.carsearch.config;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SchedulingConfigTest {
    @Test void blockedQueriesDoNotBlockMailOrPaymentChecks() throws Exception {
        var config=new SchedulingConfig();
        var workers=config.queryWorkers(1);workers.initialize();
        var mail=config.mailScheduler();mail.initialize();
        var payments=config.paymentScheduler();payments.initialize();
        var blocked=new CountDownLatch(1);var release=new CountDownLatch(1);
        var mailed=new CountDownLatch(1);var reconciled=new CountDownLatch(1);
        try {
            workers.execute(()->{blocked.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            assertTrue(blocked.await(2,TimeUnit.SECONDS));
            mail.schedule(mailed::countDown,Instant.now());
            payments.schedule(reconciled::countDown,Instant.now());
            assertTrue(mailed.await(2,TimeUnit.SECONDS));
            assertTrue(reconciled.await(2,TimeUnit.SECONDS));
            assertThrows(org.springframework.core.task.TaskRejectedException.class,()->workers.execute(()->{}));
        } finally {release.countDown();workers.shutdown();mail.shutdown();payments.shutdown();}
    }
}

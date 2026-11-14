package cn.naiblog.carsearch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.util.concurrent.ThreadPoolExecutor;

/** 查询、资金核对、邮件、余额、清理各自调度；查询领取前先检查工作线程容量。 */
@Configuration
public class SchedulingConfig {
    @Bean("queryTaskScheduler") public ThreadPoolTaskScheduler queryScheduler(){return scheduler("query-dispatch-");}
    @Bean("paymentTaskScheduler") public ThreadPoolTaskScheduler paymentScheduler(){return scheduler("payment-reconcile-");}
    @Bean("mailTaskScheduler") public ThreadPoolTaskScheduler mailScheduler(){return scheduler("mail-delivery-");}
    @Bean("balanceTaskScheduler") public ThreadPoolTaskScheduler balanceScheduler(){return scheduler("balance-refresh-");}
    @Bean("taskScheduler") public ThreadPoolTaskScheduler maintenanceScheduler(){return scheduler("maintenance-");}

    @Bean("queryWorkerExecutor")
    public ThreadPoolTaskExecutor queryWorkers(@Value("${car.query-worker-count:4}") int requested){
        int count=Math.max(1,Math.min(8,requested));
        ThreadPoolTaskExecutor executor=new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(count);executor.setMaxPoolSize(count);executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("query-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    private ThreadPoolTaskScheduler scheduler(String prefix){
        ThreadPoolTaskScheduler scheduler=new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);scheduler.setThreadNamePrefix(prefix);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}

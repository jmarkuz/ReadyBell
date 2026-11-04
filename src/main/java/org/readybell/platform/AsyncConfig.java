package org.readybell.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Executor for push delivery, plus scheduling for housekeeping jobs. */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * Push delivery runs off the request thread. {@code readybell.push.async=false} (used by tests)
     * runs it inline so results are observable immediately.
     */
    @Bean("pushExecutor")
    public TaskExecutor pushExecutor(@Value("${readybell.push.async:true}") boolean async) {
        if (!async) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("push-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}

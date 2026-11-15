package org.readybell.platform;

import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SSE heartbeat, the outbox relay poll and the housekeeping all run on the scheduler. With Spring Boot's default
 * of a single thread, one slow task (a push retrying with sleeps) stalls the others.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchedulerPoolTest {

    @Autowired private ThreadPoolTaskScheduler scheduler;
    @MockBean private NotificationSender notificationSender;

    @Test
    void scheduledTasksDoNotShareASingleThread() {
        assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isGreaterThan(1);
    }
}

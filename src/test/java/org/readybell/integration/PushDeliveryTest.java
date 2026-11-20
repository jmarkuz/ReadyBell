package org.readybell.integration;

import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.Notification;
import org.readybell.notification.domain.NotificationPayload;
import org.readybell.notification.domain.NotificationRepository;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.notification.domain.NotificationStatus;
import org.readybell.notification.domain.SendResult;
import org.readybell.platform.PushDeliveryWorker;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.DeviceSubscriptionRepository;
import org.readybell.subscription.domain.PushEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

/**
 * Push delivery on a real database and a real worker pool: a push service that hangs holds up one worker only, a
 * transient failure becomes a retry that is picked up when it is due, and two claimers never get the same notification.
 */
@SpringBootTest(properties = {
        "readybell.push.async=true",
        "readybell.push.scheduled=true",
        "readybell.push.workers=2",
        "readybell.push.retry-delays-ms=300",
        "readybell.push.poll-interval=200ms"})
@ActiveProfiles("test")
// its scheduled worker must not outlive the class and take notifications of tests that run after it
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PushDeliveryTest {

    @Autowired private NotificationRepository notifications;
    @Autowired private DeviceSubscriptionRepository subscriptions;
    @Autowired private PushDeliveryWorker worker;
    @MockBean private NotificationSender sender;

    private final TenantId tenant = new TenantId(UUID.randomUUID());

    private DeviceSubscription device(String host) {
        var d = DeviceSubscription.register(tenant, CustomerSessionId.newId(),
                new PushEndpoint("https://" + host + "/send/" + UUID.randomUUID(), "p256dh", "auth"));
        subscriptions.save(d);
        return d;
    }

    private Notification queue(DeviceSubscription device) {
        Instant now = Instant.now();
        var n = Notification.composeForDelivery(tenant, device.getId(), new NotificationPayload("Cafe", "Order #1 is ready"),
                UUID.randomUUID(), now, now, now.plus(Duration.ofMinutes(10)));
        notifications.save(n);
        return n;
    }

    private NotificationStatus statusOf(Notification n) {
        return notifications.findByTenantAndId(tenant, n.id()).orElseThrow().status();
    }

    private void await(Notification n, NotificationStatus expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (statusOf(n) != expected && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(statusOf(n)).isEqualTo(expected);
    }

    @Test
    void aHangingPushServiceHoldsUpOneWorkerAndNotTheOtherOrders() throws Exception {
        CountDownLatch hang = new CountDownLatch(1);
        when(sender.send(any(), any())).thenAnswer(inv -> {
            PushEndpoint e = inv.getArgument(0);
            if (e.endpointUrl().contains("slow.push.example")) {
                hang.await(20, TimeUnit.SECONDS);          // the push service does not answer
            }
            return SendResult.ACCEPTED;
        });
        var slow = queue(device("slow.push.example"));
        var fast = queue(device("fast.push.example"));

        worker.drain();

        await(fast, NotificationStatus.SENT);              // delivered while the other is still stuck
        assertThat(statusOf(slow)).isEqualTo(NotificationStatus.PENDING);
        hang.countDown();
        await(slow, NotificationStatus.SENT);
    }

    @Test
    void aTransientFailureIsRetriedWhenDueAndThenDelivered() throws Exception {
        when(sender.send(any(), argThat(p -> p != null)))
                .thenReturn(SendResult.TRANSIENT_FAILURE)
                .thenReturn(SendResult.ACCEPTED);
        var n = queue(device("flaky.push.example"));

        long started = System.nanoTime();
        worker.drain();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        Notification afterFailure = notifications.findByTenantAndId(tenant, n.id()).orElseThrow();
        while (afterFailure.attempts() < 1 && System.nanoTime() < deadline) {
            Thread.sleep(25);
            afterFailure = notifications.findByTenantAndId(tenant, n.id()).orElseThrow();
        }
        assertThat(afterFailure.status()).isEqualTo(NotificationStatus.PENDING);     // not failed: a retry is scheduled
        assertThat(afterFailure.attempts()).isEqualTo(1);
        assertThat(afterFailure.nextAttemptAt()).isNotNull();

        await(n, NotificationStatus.SENT);                 // the scheduled round picks the retry up when it is due
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThan(Duration.ofMillis(250));
    }

    @Test
    void aClaimedNotificationIsLeasedAndHandedOutAgainOnlyWhenTheLeaseExpires() {
        var n = queue(device("lease.push.example"));
        // no worker should take it while this test inspects the claim
        Instant now = Instant.now().plusSeconds(1);

        var first = notifications.claimDue(now, Duration.ofMinutes(2), 50);
        var second = notifications.claimDue(now, Duration.ofMinutes(2), 50);
        var afterLease = notifications.claimDue(now.plus(Duration.ofMinutes(3)), Duration.ofMinutes(2), 50);

        assertThat(first).extracting(x -> x.id().value()).contains(n.id().value());
        assertThat(second).extracting(x -> x.id().value()).doesNotContain(n.id().value());
        assertThat(afterLease).extracting(x -> x.id().value()).contains(n.id().value());
    }

    @Test
    void aSaveReleasesTheLeaseAndAFutureRetryIsNotClaimedEarly() {
        var n = queue(device("retry.push.example"));
        Instant now = Instant.now().plusSeconds(1);
        var claimed = notifications.claimDue(now, Duration.ofMinutes(2), 50).stream()
                .filter(x -> x.id().equals(n.id())).findFirst().orElseThrow();

        claimed.scheduleRetry(now.plusSeconds(60), "unavailable");
        notifications.save(claimed);

        assertThat(notifications.claimDue(now.plusSeconds(30), Duration.ofMinutes(2), 50))
                .extracting(x -> x.id().value()).doesNotContain(n.id().value());
        assertThat(notifications.claimDue(now.plusSeconds(61), Duration.ofMinutes(2), 50))
                .extracting(x -> x.id().value()).contains(n.id().value());
    }
}

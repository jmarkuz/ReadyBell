package org.readybell.notification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.readybell.notification.domain.InMemoryNotificationRepository;
import org.readybell.notification.domain.Notification;
import org.readybell.notification.domain.NotificationPayload;
import org.readybell.notification.domain.NotificationStatus;
import org.readybell.notification.domain.PushRetryPolicy;
import org.readybell.notification.domain.SendResult;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.RecordingDomainEventPublisher;
import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.application.ManageSubscriptionUseCase;
import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.InMemoryDeviceSubscriptionRepository;
import org.readybell.subscription.domain.PushEndpoint;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** One attempt per call; a transient failure becomes a later due time, never a sleeping thread. */
class PushDeliveryServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-20T12:00:00Z");

    /** A clock the test moves. */
    private static final class TestClock extends Clock {
        Instant now = T0;
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final TestClock clock = new TestClock();
    private final List<SendResult> script = new ArrayList<>();
    private final List<NotificationPayload> sent = new ArrayList<>();
    private InMemoryDeviceSubscriptionRepository subscriptions;
    private InMemoryNotificationRepository notifications;
    private RecordingDomainEventPublisher events;
    private ManageSubscriptionUseCase subscriptionUseCase;
    private DeviceSubscription device;

    private PushDeliveryService service(Duration... retryDelays) {
        return new PushDeliveryService(notifications, subscriptions, (endpoint, payload) -> {
            sent.add(payload);
            return script.isEmpty() ? SendResult.ACCEPTED : script.remove(0);
        }, events, subscriptionUseCase, new PushRetryPolicy(List.of(retryDelays)), clock);
    }

    @BeforeEach
    void setUp() {
        subscriptions = new InMemoryDeviceSubscriptionRepository();
        notifications = new InMemoryNotificationRepository();
        events = new RecordingDomainEventPublisher();
        subscriptionUseCase = Mockito.mock(ManageSubscriptionUseCase.class);
        device = DeviceSubscription.register(tenant, CustomerSessionId.newId(),
                new PushEndpoint("https://push.example/1", "p256dh", "auth"));
        subscriptions.save(device);
    }

    private Notification queued(Instant expiresAt) {
        var n = Notification.composeForDelivery(tenant, device.getId(),
                new NotificationPayload("Café Aurora", "Order #7 is ready for pickup!", "/track/x"),
                UUID.randomUUID(), T0, T0, expiresAt);
        notifications.save(n);
        return n;
    }

    @Test
    void anAcceptedPushIsSentAndAnnounced() {
        Notification n = queued(T0.plus(Duration.ofMinutes(10)));

        service().attempt(n);

        assertThat(n.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.nextAttemptAt()).isNull();
        assertThat(events.getPublishedEvents()).anyMatch(e -> e.getClass().getSimpleName().equals("NotificationSent"));
        assertThat(sent).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("Café Aurora");
            assertThat(m.body()).isEqualTo("Order #7 is ready for pickup!");
            assertThat(m.url()).isEqualTo("/track/x");
        });
    }

    @Test
    void aTransientFailureStaysPendingAndIsDueLaterWithoutWaiting() {
        script.add(SendResult.TRANSIENT_FAILURE);
        Notification n = queued(T0.plus(Duration.ofMinutes(10)));

        long started = System.nanoTime();
        service(Duration.ofSeconds(5), Duration.ofSeconds(30)).attempt(n);

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));   // no sleeping
        assertThat(n.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(n.attempts()).isEqualTo(1);
        assertThat(n.nextAttemptAt()).isEqualTo(T0.plusSeconds(5));
        assertThat(n.isDue(T0.plusSeconds(4))).isFalse();
        assertThat(n.isDue(T0.plusSeconds(5))).isTrue();
        assertThat(n.lastError()).isNotBlank();
    }

    @Test
    void theBackoffGrowsAndTheNotificationIsDeliveredOnceTheServiceRecovers() {
        script.add(SendResult.TRANSIENT_FAILURE);
        script.add(SendResult.TRANSIENT_FAILURE);
        Notification n = queued(T0.plus(Duration.ofMinutes(10)));
        var service = service(Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofMinutes(2));

        service.attempt(n);                                  // fails, retry in 5 s
        clock.now = T0.plusSeconds(5);
        service.attempt(n);                                  // fails, retry in 30 s
        assertThat(n.nextAttemptAt()).isEqualTo(T0.plusSeconds(35));
        clock.now = T0.plusSeconds(35);
        service.attempt(n);                                  // accepted

        assertThat(n.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent).hasSize(3);
    }

    @Test
    void whenTheRetriesAreUsedUpItFails() {
        for (int i = 0; i < 5; i++) script.add(SendResult.TRANSIENT_FAILURE);
        Notification n = queued(T0.plus(Duration.ofMinutes(10)));
        var service = service(Duration.ofSeconds(1), Duration.ofSeconds(1));

        service.attempt(n);
        service.attempt(n);
        service.attempt(n);                                  // first attempt + 2 retries

        assertThat(n.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.lastError()).contains("retries used up");
        assertThat(n.nextAttemptAt()).isNull();
        assertThat(sent).hasSize(3);
        assertThat(events.hasNoPublishedEvents()).isTrue();
    }

    @Test
    void withoutRetriesATransientFailureFailsAtOnce() {
        script.add(SendResult.TRANSIENT_FAILURE);
        Notification n = queued(null);

        service().attempt(n);

        assertThat(n.status()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void aRetryThatWouldFallAfterTheDeadlineIsNotScheduled() {
        script.add(SendResult.TRANSIENT_FAILURE);
        Notification n = queued(T0.plusSeconds(10));

        service(Duration.ofMinutes(2)).attempt(n);

        assertThat(n.status()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void aNotificationPastItsDeadlineIsDroppedWithoutSending() {
        Notification n = queued(T0.plusSeconds(10));
        clock.now = T0.plusSeconds(11);

        service(Duration.ofSeconds(1)).attempt(n);

        assertThat(n.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.lastError()).contains("expired");
        assertThat(sent).isEmpty();                          // "your order is ready" 11 s late is dropped here, 10 min in real use
    }

    @Test
    void rejectionIsNotRetried() {
        script.add(SendResult.REJECTED);
        Notification n = queued(T0.plus(Duration.ofMinutes(10)));

        service(Duration.ofSeconds(1), Duration.ofSeconds(1)).attempt(n);

        assertThat(n.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(sent).hasSize(1);
    }

    @Test
    void anExpiredEndpointFailsTheNotificationAndDeletesTheSubscription() {
        script.add(SendResult.ENDPOINT_EXPIRED);
        Notification n = queued(T0.plus(Duration.ofMinutes(10)));

        service(Duration.ofSeconds(1)).attempt(n);

        assertThat(n.status()).isEqualTo(NotificationStatus.FAILED);
        verify(subscriptionUseCase).deleteExpiredSubscription(device.getId());
    }

    @Test
    void aSubscriptionThatIsGoneFailsTheNotificationWithoutSending() {
        Notification n = queued(T0.plus(Duration.ofMinutes(10)));
        subscriptions.delete(device.getId());

        service(Duration.ofSeconds(1)).attempt(n);

        assertThat(n.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.lastError()).contains("subscription");
        assertThat(sent).isEmpty();
    }

    @Test
    void anUnexpectedSenderExceptionIsTreatedAsTransientAndDoesNotEscape() {
        Notification n = queued(T0.plus(Duration.ofMinutes(10)));
        var service = new PushDeliveryService(notifications, subscriptions, (e, p) -> { throw new IllegalStateException("boom"); },
                events, subscriptionUseCase, new PushRetryPolicy(List.of(Duration.ofSeconds(5))), clock);

        service.attempt(n);

        assertThat(n.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(n.nextAttemptAt()).isEqualTo(T0.plusSeconds(5));
    }
}

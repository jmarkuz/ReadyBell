package org.readybell.platform.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationPayload;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.notification.domain.SendResult;
import org.readybell.ordering.adapter.in.web.sse.SseEmitterRegistry;
import org.readybell.ordering.domain.OrderCreated;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.ordering.domain.OrderStatusUpdated;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.session.domain.CustomerSessionStarted;
import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.domain.PushEndpoint;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessMetricsTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:01.500Z");
    private static final PushEndpoint ENDPOINT = new PushEndpoint("https://push.example/1", "k", "a");
    private static final NotificationPayload PAYLOAD = new NotificationPayload("t", "b");

    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final CustomerSessionId session = CustomerSessionId.newId();
    private MeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
    }

    @Test
    void domainEventsIncrementBusinessCounters() {
        BusinessMetrics metrics = new BusinessMetrics(registry, new SseEmitterRegistry());

        metrics.onSessionStarted(CustomerSessionStarted.of(session, tenant, NOW));
        metrics.onSessionStarted(CustomerSessionStarted.of(session, tenant, NOW));
        metrics.onOrderCreated(OrderCreated.of(OrderId.newId(), tenant, session, NOW, 1));
        metrics.onOrderStatusUpdated(OrderStatusUpdated.of(OrderId.newId(), tenant, session,
                OrderStatus.IN_PROGRESS, OrderStatus.READY, NOW));

        assertThat(registry.counter("readybell.sessions.started").count()).isEqualTo(2);
        assertThat(registry.counter("readybell.orders.created").count()).isEqualTo(1);
        assertThat(registry.counter("readybell.orders.transitions", "status", "READY").count()).isEqualTo(1);
        assertThat(registry.counter("readybell.orders.transitions", "status", "CANCELLED").count()).isZero();
    }

    @Test
    void sseGaugesFollowOpenConnectionsPerAudience() {
        SseEmitterRegistry sse = new SseEmitterRegistry();
        new BusinessMetrics(registry, sse);

        sse.registerStaff(new TenantId(UUID.randomUUID()));
        sse.registerStaff(new TenantId(UUID.randomUUID()));
        sse.registerCustomer(session);

        assertThat(registry.get("readybell.sse.connections").tag("audience", "staff").gauge().value()).isEqualTo(2);
        assertThat(registry.get("readybell.sse.connections").tag("audience", "customer").gauge().value()).isEqualTo(1);
    }

    @Test
    void pushAttemptsAreCountedByResultAndLatencyIsRecordedOnAcceptOnly() {
        PushMetrics push = new PushMetrics(registry, Clock.fixed(NOW, ZoneOffset.UTC));
        SendResult[] script = {SendResult.ACCEPTED, SendResult.REJECTED, SendResult.TRANSIENT_FAILURE,
                SendResult.ENDPOINT_EXPIRED};
        int[] i = {0};
        NotificationSender sender = new MeteredNotificationSender((e, p) -> script[i[0]++], push);

        push.whileHandling(NOW.minusMillis(800), () -> {
            for (int n = 0; n < script.length; n++) {
                sender.send(ENDPOINT, PAYLOAD);
            }
        });

        assertThat(registry.counter("readybell.push.attempts", "result", "sent").count()).isEqualTo(1);
        assertThat(registry.counter("readybell.push.attempts", "result", "failed").count()).isEqualTo(2);
        assertThat(registry.counter("readybell.push.attempts", "result", "expired").count()).isEqualTo(1);
        var timer = registry.get("readybell.push.ready.latency").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(800);
    }

    @Test
    void noLatencyIsRecordedOutsideAnOrderReadyHandling() {
        PushMetrics push = new PushMetrics(registry, Clock.fixed(NOW, ZoneOffset.UTC));
        new MeteredNotificationSender((e, p) -> SendResult.ACCEPTED, push).send(ENDPOINT, PAYLOAD);

        assertThat(registry.counter("readybell.push.attempts", "result", "sent").count()).isEqualTo(1);
        assertThat(registry.get("readybell.push.ready.latency").timer().count()).isZero();
    }

    @Test
    void senderExceptionCountsAsFailedAndPropagates() {
        PushMetrics push = new PushMetrics(registry, Clock.fixed(NOW, ZoneOffset.UTC));
        NotificationSender boom = (e, p) -> { throw new IllegalStateException("down"); };

        assertThatThrownBy(() -> new MeteredNotificationSender(boom, push).send(ENDPOINT, PAYLOAD))
                .isInstanceOf(IllegalStateException.class);
        assertThat(registry.counter("readybell.push.attempts", "result", "failed").count()).isEqualTo(1);
    }
}

package org.readybell.integration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.ordering.adapter.in.web.sse.SseEmitterRegistry;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.ordering.domain.OrderStatusUpdated;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Order status events reach SSE clients locally, and via Redis pub/sub when enabled. */
class SseFanoutTest {

    /** Emitter that records what would have been written to the client. */
    static class CapturingEmitter extends SseEmitter {
        /** Every data chunk written (Spring splits one event into several chunks). */
        final List<String> sent = new CopyOnWriteArrayList<>();

        /** Payloads of delivered events, one entry per event. */
        List<String> events() {
            return sent.stream().filter(c -> c.contains("\"orderId\"")).toList();
        }
        CapturingEmitter() { super(0L); }
        @Override
        public synchronized void send(SseEventBuilder builder) {
            builder.build().forEach(d -> sent.add(String.valueOf(d.getData())));
        }
    }

    static OrderStatusUpdated event(CustomerSessionId session) {
        return OrderStatusUpdated.of(OrderId.newId(), new TenantId(UUID.randomUUID()), session,
                OrderStatus.CREATED, OrderStatus.IN_PROGRESS, Instant.now());
    }

    static void awaitSize(CapturingEmitter e, int size) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (e.events().size() < size && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class Local {
        @Autowired SseEmitterRegistry registry;
        @Autowired ApplicationEventPublisher events;
        @MockBean NotificationSender notificationSender;

        @Test
        void deliversToMatchingSessionOnly() throws Exception {
            var session = new CustomerSessionId(UUID.randomUUID());
            var mine = new CapturingEmitter();
            var other = new CapturingEmitter();
            registry.registerCustomer(session, mine);
            registry.registerCustomer(new CustomerSessionId(UUID.randomUUID()), other);

            var e = event(session);
            events.publishEvent(e);

            awaitSize(mine, 1);
            assertThat(mine.events()).hasSize(1);
            assertThat(mine.events().get(0))
                    .contains(e.orderId().value().toString()).contains("IN_PROGRESS");
            assertThat(other.events()).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(properties = "readybell.sse.redis.enabled=true")
    @ActiveProfiles("test")
    class ViaRedis {
        @Autowired SseEmitterRegistry registry;
        @Autowired ApplicationEventPublisher events;
        @MockBean NotificationSender notificationSender;

        @Test
        void deliversExactlyOnceThroughRedisRelay() throws Exception {
            var session = new CustomerSessionId(UUID.randomUUID());
            var emitter = new CapturingEmitter();
            registry.registerCustomer(session, emitter);

            events.publishEvent(event(session));

            awaitSize(emitter, 1);
            Thread.sleep(300); // a duplicate (local + relayed) would show up by now
            assertThat(emitter.events()).hasSize(1);
        }
    }
}

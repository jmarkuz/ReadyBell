package org.readybell.integration;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderReady;
import org.readybell.platform.SpringOrderReadyEventListener;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.session.domain.CustomerSessionStarted;
import org.readybell.shared.domain.TenantId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The meters are wired into the real context: events published on the Spring bus move them. */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(IntegrationTestConfig.class)
class BusinessMetricsIntegrationTest {

    @Autowired private MeterRegistry meters;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private SpringOrderReadyEventListener listener;

    @Test
    void sessionStartedEventIncrementsTheCounterInTheSpringContext() {
        double before = meters.counter("readybell.sessions.started").count();

        events.publishEvent(CustomerSessionStarted.of(
                CustomerSessionId.newId(), new TenantId(UUID.randomUUID()), Instant.now()));

        assertThat(meters.counter("readybell.sessions.started").count()).isEqualTo(before + 1);
    }

    @Test
    void orderReadyWithoutSubscriptionsLeavesPushMetersRegisteredAndUnchanged() {
        double before = meters.counter("readybell.push.attempts", "result", "sent").count();

        listener.onOrderReady(OrderReady.of(OrderId.newId(), new TenantId(UUID.randomUUID()),
                CustomerSessionId.newId(), Instant.now()));

        assertThat(meters.counter("readybell.push.attempts", "result", "sent").count()).isEqualTo(before);
        assertThat(meters.find("readybell.push.ready.latency").timer()).isNotNull();
    }
}

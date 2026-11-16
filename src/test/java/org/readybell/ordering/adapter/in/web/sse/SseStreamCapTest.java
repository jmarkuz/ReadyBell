package org.readybell.ordering.adapter.in.web.sse;

import org.junit.jupiter.api.Test;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.testsupport.RecordingEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A script cannot hold unlimited streams open, but a reloaded page always gets its stream. */
class SseStreamCapTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry();

    @Test
    void aSessionKeepsAtMostFiveCustomerStreamsAndTheNewestWins() {
        var session = new CustomerSessionId(UUID.randomUUID());
        var emitters = new RecordingEmitter[8];
        for (int i = 0; i < emitters.length; i++) {
            emitters[i] = new RecordingEmitter();
            registry.registerCustomer(session, emitters[i]);
        }

        assertThat(registry.customerConnections()).isEqualTo(SseEmitterRegistry.MAX_CUSTOMER_STREAMS_PER_SESSION);
        // the three oldest were closed, the latest five are open and receive events
        registry.deliver(new SseMessage(UUID.randomUUID(), session.value(), UUID.randomUUID(), "READY"));
        assertThat(emitters[0].text()).doesNotContain("READY");
        assertThat(emitters[2].text()).doesNotContain("READY");
        assertThat(emitters[7].text()).contains("READY");
        assertThat(emitters[3].text()).contains("READY");
    }

    @Test
    void otherSessionsAreNotAffected() {
        var busy = new CustomerSessionId(UUID.randomUUID());
        var other = new CustomerSessionId(UUID.randomUUID());
        for (int i = 0; i < 20; i++) registry.registerCustomer(busy, new RecordingEmitter());
        registry.registerCustomer(other, new RecordingEmitter());

        assertThat(registry.customerConnections()).isEqualTo(SseEmitterRegistry.MAX_CUSTOMER_STREAMS_PER_SESSION + 1);
    }

    @Test
    void staffStreamsOfOneRestaurantAreCappedToo() {
        var tenant = new TenantId(UUID.randomUUID());
        for (int i = 0; i < SseEmitterRegistry.MAX_STAFF_STREAMS_PER_RESTAURANT + 10; i++) {
            registry.registerStaff(tenant, new RecordingEmitter());
        }
        assertThat(registry.staffConnections()).isEqualTo(SseEmitterRegistry.MAX_STAFF_STREAMS_PER_RESTAURANT);
    }
}

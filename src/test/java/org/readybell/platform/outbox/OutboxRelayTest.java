package org.readybell.platform.outbox;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.ordering.domain.OrderStatusUpdated;
import org.readybell.platform.SpringDomainEventPublisherAdapter;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.TenantId;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final OutboxEventSerializer serializer = new OutboxEventSerializer(mapper);

    private static OrderStatusUpdated event() {
        return OrderStatusUpdated.of(OrderId.newId(), new TenantId(UUID.randomUUID()),
                new CustomerSessionId(UUID.randomUUID()), OrderStatus.CREATED, OrderStatus.READY, Instant.now());
    }

    @Test
    void eventSurvivesJsonRoundTrip() {
        var original = event();
        DomainEvent back = serializer.fromJson(OrderStatusUpdated.class.getName(), serializer.toJson(original));
        assertThat(back).isEqualTo(original);
    }

    @Test
    void onlyDomainEventsOfTheApplicationCanBeRead() {
        assertThatThrownBy(() -> serializer.fromJson("java.lang.String", "\"x\""))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> serializer.fromJson("no.such.Type", "{}")).isInstanceOf(RuntimeException.class);
    }

    private OutboxRelay relay(OutboxStore store, SpringDomainEventPublisherAdapter dispatcher, int maxAttempts) {
        var props = new OutboxProperties();
        props.setMaxAttempts(maxAttempts);
        return new OutboxRelay(store, serializer, dispatcher, props, Clock.systemUTC(), new SimpleMeterRegistry());
    }

    @Test
    void failingHandlerIsRetriedWithBackoffAndThenParked() {
        var store = mock(OutboxStore.class);
        var dispatcher = mock(SpringDomainEventPublisherAdapter.class);
        var e = event();
        var claimed = new OutboxStore.Claimed(e.eventId(), OrderStatusUpdated.class.getName(), serializer.toJson(e), 0);
        var lastAttempt = new OutboxStore.Claimed(e.eventId(), OrderStatusUpdated.class.getName(), serializer.toJson(e), 2);
        when(store.claim(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(claimed)).thenReturn(List.of())
                .thenReturn(List.of(lastAttempt)).thenReturn(List.of());
        doThrow(new IllegalStateException("push down")).when(dispatcher).publish(any());
        var relay = relay(store, dispatcher, 3);

        relay.drain();
        verify(store).markFailed(eq(e.eventId()), any(), any(), eq(false), anyString());   // first failure: retry later

        relay.drain();
        verify(store).markFailed(eq(e.eventId()), any(), any(), eq(true), anyString());    // third failure: parked
    }

    @Test
    void successMarksTheEventProcessed() {
        var store = mock(OutboxStore.class);
        var dispatcher = mock(SpringDomainEventPublisherAdapter.class);
        var e = event();
        when(store.claim(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new OutboxStore.Claimed(e.eventId(), OrderStatusUpdated.class.getName(),
                        serializer.toJson(e), 0)))
                .thenReturn(List.of());

        int handled = relay(store, dispatcher, 3).drain();

        assertThat(handled).isEqualTo(1);
        verify(dispatcher).publish(e);
        verify(store).markProcessed(eq(e.eventId()), any());
    }

    @Test
    void backoffGrowsAndIsCapped() {
        var relay = relay(mock(OutboxStore.class), mock(SpringDomainEventPublisherAdapter.class), 10);
        assertThat(relay.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(relay.backoff(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(relay.backoff(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(relay.backoff(30)).isEqualTo(Duration.ofMinutes(5));
    }
}

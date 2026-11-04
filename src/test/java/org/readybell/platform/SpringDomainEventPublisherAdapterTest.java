package org.readybell.platform;

import org.junit.jupiter.api.Test;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.ordering.domain.OrderStatusUpdated;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.EventPublicationException;
import org.readybell.shared.domain.TenantId;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpringDomainEventPublisherAdapterTest {

    private final ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
    private final SpringDomainEventPublisherAdapter adapter = new SpringDomainEventPublisherAdapter(spring);

    private static OrderStatusUpdated event() {
        return OrderStatusUpdated.of(OrderId.newId(), new TenantId(UUID.randomUUID()),
                new CustomerSessionId(UUID.randomUUID()), OrderStatus.CREATED, OrderStatus.IN_PROGRESS, Instant.now());
    }

    @Test
    void publishHandsTheEventToSpring() {
        var e = event();
        adapter.publish(e);
        verify(spring).publishEvent((Object) e);
    }

    @Test
    void publishFailureNamesTheEvent() {
        var e = event();
        doThrow(new IllegalStateException("listener broke")).when(spring).publishEvent((Object) e);

        assertThatThrownBy(() -> adapter.publish(e))
                .isInstanceOf(EventPublicationException.class)
                .hasMessageContaining("listener broke")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void publishAllTriesEveryEventAndThenReportsTheFirstFailure() {
        var first = event();
        var failing = event();
        var last = event();
        doThrow(new IllegalStateException("boom")).when(spring).publishEvent((Object) failing);

        assertThatThrownBy(() -> adapter.publishAll(List.<DomainEvent>of(first, failing, last)))
                .isInstanceOf(EventPublicationException.class);

        verify(spring).publishEvent((Object) first);
        verify(spring).publishEvent((Object) last);           // not skipped because of the failure before it
    }

    @Test
    void publishAllWithoutFailuresReturnsNormally() {
        adapter.publishAll(List.<DomainEvent>of(event(), event()));
        assertThat(true).isTrue();
    }

    @Test
    void nullsAreRejected() {
        assertThatThrownBy(() -> adapter.publish(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.publishAll(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SpringDomainEventPublisherAdapter(null)).isInstanceOf(NullPointerException.class);
    }
}

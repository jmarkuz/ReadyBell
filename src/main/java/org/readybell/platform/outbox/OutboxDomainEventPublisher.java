package org.readybell.platform.outbox;

import org.readybell.ordering.domain.OrderReady;
import org.readybell.ordering.domain.OrderStatusUpdated;
import org.readybell.platform.SpringDomainEventPublisherAdapter;
import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.DomainEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The {@link DomainEventPublisher} of the application. {@link OrderReady} and {@link OrderStatusUpdated} are
 * written to the outbox in the caller's transaction (they must not be lost); other events go straight to the
 * in-process bus as before.
 */
@Component
@Primary
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private static final Set<Class<? extends DomainEvent>> OUTBOXED = Set.of(OrderReady.class, OrderStatusUpdated.class);

    private final SpringDomainEventPublisherAdapter direct;
    private final OutboxStore store;
    private final OutboxEventSerializer serializer;
    private final OutboxRelay relay;
    private final Clock clock;

    public OutboxDomainEventPublisher(SpringDomainEventPublisherAdapter direct, OutboxStore store,
                                      OutboxEventSerializer serializer, OutboxRelay relay, Clock clock) {
        this.direct = direct;
        this.store = store;
        this.serializer = serializer;
        this.relay = relay;
        this.clock = clock;
    }

    @Override
    public void publish(DomainEvent event) {
        publishAll(List.of(event));
    }

    @Override
    public void publishAll(List<DomainEvent> events) {
        Objects.requireNonNull(events, "events must not be null");
        boolean appended = false;
        for (DomainEvent event : events) {
            if (OUTBOXED.contains(event.getClass())) {
                store.append(event, serializer.toJson(event), clock.instant());
                appended = true;
            } else {
                direct.publish(event);
            }
        }
        if (appended) {
            wakeAfterCommit();
        }
    }

    private void wakeAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    relay.wake();
                }
            });
        } else {
            relay.wake();
        }
    }
}

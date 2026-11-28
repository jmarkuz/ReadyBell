package org.readybell.ordering.domain;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event emitted when a new {@link Order} is created.
 *
 * <p>Published through the {@code DomainEventPublisher} port after a successful
 * order creation.
 */
public record OrderCreated(
        UUID eventId,
        OrderId orderId,
        TenantId tenantId,
        CustomerSessionId sessionId,
        Instant occurredAt,
        int orderNumber
) implements DomainEvent {
    public OrderCreated {
        Objects.requireNonNull(eventId,   "eventId must not be null");
        Objects.requireNonNull(orderId,   "orderId must not be null");
        Objects.requireNonNull(tenantId,  "tenantId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /** Convenience factory that generates a fresh {@code eventId} automatically. */
    public static OrderCreated of(OrderId orderId,
                                   TenantId tenantId,
                                   CustomerSessionId sessionId,
                                   Instant occurredAt,
                                   int orderNumber) {
        return new OrderCreated(UUID.randomUUID(), orderId, tenantId, sessionId, occurredAt, orderNumber);
    }
}

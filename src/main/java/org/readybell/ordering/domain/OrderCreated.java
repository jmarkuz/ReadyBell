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
 * order creation (Req 4.3, 11.3).
 *
 * @param eventId     unique identity of this event occurrence
 * @param orderId     identity of the newly created order
 * @param tenantId    the restaurant this order belongs to (Req 4.2, 10.1)
 * @param sessionId   the customer session this order is associated with (Req 4.4)
 * @param occurredAt  the instant at which the order was created
 */
public record OrderCreated(
        UUID eventId,
        OrderId orderId,
        TenantId tenantId,
        CustomerSessionId sessionId,
        Instant occurredAt
) implements DomainEvent {

    public OrderCreated {
        Objects.requireNonNull(eventId,   "eventId must not be null");
        Objects.requireNonNull(orderId,   "orderId must not be null");
        Objects.requireNonNull(tenantId,  "tenantId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Convenience factory that generates a fresh {@code eventId} automatically.
     */
    public static OrderCreated of(OrderId orderId,
                                   TenantId tenantId,
                                   CustomerSessionId sessionId,
                                   Instant occurredAt) {
        return new OrderCreated(UUID.randomUUID(), orderId, tenantId, sessionId, occurredAt);
    }
}

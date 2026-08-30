package com.example.ordering.domain;

import com.example.session.domain.CustomerSessionId;
import com.example.shared.domain.DomainEvent;
import com.example.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event emitted when an {@link Order} transitions to the {@link OrderStatus#READY} state.
 *
 * <p>This event triggers the notification flow: the Notification context listens for
 * {@code OrderReady} and sends a Web Push notification to the customer's device
 * subscriptions (Req 5.6, 6.1, 11.3).
 *
 * @param eventId     unique identity of this event occurrence
 * @param orderId     identity of the order that became ready
 * @param tenantId    the restaurant this order belongs to (Req 10.1)
 * @param sessionId   the customer session associated with the order (Req 6.1)
 * @param occurredAt  the instant at which the order became ready
 */
public record OrderReady(
        UUID eventId,
        OrderId orderId,
        TenantId tenantId,
        CustomerSessionId sessionId,
        Instant occurredAt
) implements DomainEvent {

    public OrderReady {
        Objects.requireNonNull(eventId,   "eventId must not be null");
        Objects.requireNonNull(orderId,   "orderId must not be null");
        Objects.requireNonNull(tenantId,  "tenantId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Convenience factory that generates a fresh {@code eventId} automatically.
     */
    public static OrderReady of(OrderId orderId,
                                 TenantId tenantId,
                                 CustomerSessionId sessionId,
                                 Instant occurredAt) {
        return new OrderReady(UUID.randomUUID(), orderId, tenantId, sessionId, occurredAt);
    }
}

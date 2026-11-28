package org.readybell.ordering.domain;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event emitted when an {@link Order} transitions to the {@link OrderStatus#READY} state.
 *
 * <p>This event triggers the notification flow: the Notification context listens for
 * {@code OrderReady} and sends a Web Push notification to the customer's device
 * subscriptions.
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

    /** Convenience factory that generates a fresh {@code eventId} automatically. */
    public static OrderReady of(OrderId orderId,
                                 TenantId tenantId,
                                 CustomerSessionId sessionId,
                                 Instant occurredAt) {
        return new OrderReady(UUID.randomUUID(), orderId, tenantId, sessionId, occurredAt);
    }
}

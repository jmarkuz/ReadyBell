package com.example.ordering.domain;

import com.example.session.domain.CustomerSessionId;
import com.example.shared.domain.DomainEvent;
import com.example.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain event emitted whenever an {@link Order} transitions from one status to another.
 *
 * <p>Published through the {@code DomainEventPublisher} port on every successful
 * order status change (Req 5.5, 11.3).
 *
 * @param eventId     unique identity of this event occurrence
 * @param orderId     identity of the order whose status changed
 * @param tenantId    the restaurant this order belongs to (Req 10.1)
 * @param sessionId   the customer session this order is associated with
 * @param fromStatus  the previous {@link OrderStatus}
 * @param toStatus    the new {@link OrderStatus}
 * @param occurredAt  the instant at which the transition occurred
 */
public record OrderStatusUpdated(
        UUID eventId,
        OrderId orderId,
        TenantId tenantId,
        CustomerSessionId sessionId,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        Instant occurredAt
) implements DomainEvent {

    public OrderStatusUpdated {
        Objects.requireNonNull(eventId,    "eventId must not be null");
        Objects.requireNonNull(orderId,    "orderId must not be null");
        Objects.requireNonNull(tenantId,   "tenantId must not be null");
        Objects.requireNonNull(sessionId,  "sessionId must not be null");
        Objects.requireNonNull(fromStatus, "fromStatus must not be null");
        Objects.requireNonNull(toStatus,   "toStatus must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * Convenience factory that generates a fresh {@code eventId} automatically.
     */
    public static OrderStatusUpdated of(OrderId orderId,
                                         TenantId tenantId,
                                         CustomerSessionId sessionId,
                                         OrderStatus fromStatus,
                                         OrderStatus toStatus,
                                         Instant occurredAt) {
        return new OrderStatusUpdated(
                UUID.randomUUID(), orderId, tenantId, sessionId, fromStatus, toStatus, occurredAt);
    }
}

package org.readybell.ordering.domain;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The customer opened the tracking link of an order for the first time (the QR code was scanned). */
public record OrderConnected(
        UUID eventId,
        OrderId orderId,
        TenantId tenantId,
        CustomerSessionId sessionId,
        Instant occurredAt,
        int orderNumber
) implements DomainEvent {
    public OrderConnected {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static OrderConnected of(OrderId orderId, TenantId tenantId, CustomerSessionId sessionId,
                                    Instant occurredAt, int orderNumber) {
        return new OrderConnected(UUID.randomUUID(), orderId, tenantId, sessionId, occurredAt, orderNumber);
    }
}

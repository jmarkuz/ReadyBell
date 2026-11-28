package org.readybell.ordering.adapter.in.web;

import org.readybell.ordering.domain.Order;

import java.time.Instant;
import java.util.UUID;

/** Result of creating or transitioning an order. */
public record OrderResponse(UUID id, String orderId, UUID sessionId, String status, long version,
                            Instant createdAt, Instant updatedAt) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId().value(),
                OrderRowResponse.displayNumber(order),
                order.getSessionId().value(),
                order.getStatus().name(),
                order.getVersion(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}

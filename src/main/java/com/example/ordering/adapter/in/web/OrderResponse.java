package com.example.ordering.adapter.in.web;

import com.example.ordering.domain.Order;

import java.util.UUID;

/**
 * Response body returned by all Order Management API endpoints.
 *
 * <p>Carries the full order summary:
 * <ul>
 *   <li>{@code orderId}   — the order identity (Req 4.1)</li>
 *   <li>{@code sessionId} — the customer session the order belongs to (Req 4.4)</li>
 *   <li>{@code status}    — the current lifecycle status (Req 5.1)</li>
 *   <li>{@code version}   — optimistic-lock version for concurrent-update detection (Req 5.8)</li>
 * </ul>
 *
 * @param orderId   the order's UUID
 * @param sessionId the customer session's UUID
 * @param status    the current order status label (e.g. {@code "CREATED"})
 * @param version   the current optimistic-lock version
 */
public record OrderResponse(UUID orderId, UUID sessionId, String status, long version) {

    /**
     * Creates an {@code OrderResponse} from an {@link Order} aggregate.
     *
     * @param order the source order (must not be null)
     * @return a response record populated from the order's fields
     */
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId().value(),
                order.getSessionId().value(),
                order.getStatus().name(),
                order.getVersion()
        );
    }
}

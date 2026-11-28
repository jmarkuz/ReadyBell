package org.readybell.ordering.adapter.in.web;

import org.readybell.ordering.domain.Order;

import java.util.UUID;

/**
 * Response body representing one order and its current state, returned by
 * {@code GET /api/sessions/{id}/orders}.
 */
public record OrderStateResponse(UUID orderId, String status) {
    /** Constructs an {@code OrderStateResponse} from the given {@link Order} aggregate. */
    public static OrderStateResponse from(Order order) {
        return new OrderStateResponse(
                order.getId().value(),
                order.getStatus().name()
        );
    }
}

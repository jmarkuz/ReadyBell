package com.example.session.adapter.in.web;

import com.example.ordering.domain.Order;

import java.util.UUID;

/**
 * Response body representing one order and its current state, returned by
 * {@code GET /api/sessions/{id}/orders}.
 *
 * <p>Requirements covered: 2.2, 2.5.
 *
 * @param orderId the order identifier
 * @param status  the current order status label (e.g. "CREATED", "IN_PROGRESS")
 */
public record OrderStateResponse(UUID orderId, String status) {

    /**
     * Constructs an {@code OrderStateResponse} from the given {@link Order} aggregate.
     *
     * @param order the order to represent; must not be null
     * @return a response record populated from the order's state
     */
    public static OrderStateResponse from(Order order) {
        return new OrderStateResponse(
                order.getId().value(),
                order.getStatus().name()
        );
    }
}

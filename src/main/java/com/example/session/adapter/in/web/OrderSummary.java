package com.example.session.adapter.in.web;

import com.example.ordering.domain.Order;
import com.example.ordering.domain.OrderStatus;

/**
 * Compact summary of an order and its current lifecycle state, returned by
 * {@code GET /api/sessions/{id}/orders}.
 *
 * <p>Exposes only the fields required by the Customer Session API contract
 * (Req 2.2, 2.5): the order identity and the current {@link OrderStatus} label.
 *
 * @param orderId the order identifier as a string
 * @param status  the current order status label (e.g. "CREATED", "IN_PROGRESS")
 */
public record OrderSummary(String orderId, String status) {

    /**
     * Creates an {@code OrderSummary} from the given {@link Order} aggregate.
     *
     * @param order the source order; must not be null
     * @return a summary record populated from the order's identity and status
     */
    public static OrderSummary from(Order order) {
        return new OrderSummary(
                order.getId().value().toString(),
                order.getStatus().name()
        );
    }
}

package org.readybell.ordering.adapter.in.web;

/**
 * Request body for {@code PATCH /api/orders/{id}/status}.
 *
 * <p>The caller provides the target status name (e.g. {@code "IN_PROGRESS"}).
 * Valid values are the names of {@link org.readybell.ordering.domain.OrderStatus}
 * enum constants.
 *
 * @param status the target order status label (case-sensitive, must match an
 *               {@code OrderStatus} enum constant name)
 */
public record TransitionOrderRequest(String status) {}

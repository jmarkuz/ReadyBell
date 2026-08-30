package com.example.ordering.adapter.in.web;

import com.example.ordering.domain.OrderStatus;

/**
 * Request body for {@code PATCH /api/orders/{id}/status}.
 *
 * <p>The caller provides the target status name (e.g. {@code "IN_PROGRESS"}).
 * Valid values are the names of {@link OrderStatus}
 * enum constants.
 *
 * @param status the target order status label (case-sensitive, must match an
 *               {@code OrderStatus} enum constant name)
 */
public record TransitionOrderRequest(String status) {}

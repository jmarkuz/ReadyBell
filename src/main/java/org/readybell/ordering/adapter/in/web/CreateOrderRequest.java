package org.readybell.ordering.adapter.in.web;

import java.util.UUID;

/**
 * Request body for {@code POST /api/orders}.
 *
 * <p>The caller provides the customer session UUID that the new order will be
 * associated with (Req 4.4). The tenant is resolved server-side from the
 * authenticated principal's context.
 *
 * @param sessionId the customer session UUID this order belongs to
 */
public record CreateOrderRequest(UUID sessionId) {}

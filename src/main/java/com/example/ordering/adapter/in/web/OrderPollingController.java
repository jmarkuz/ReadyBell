package com.example.ordering.adapter.in.web;

import com.example.ordering.domain.OrderRepository;
import com.example.session.domain.CustomerSessionId;
import com.example.shared.domain.TenantId;
import com.example.shared.tenancy.TenantContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Polling fallback endpoint for customers who cannot maintain an SSE connection.
 *
 * <p>Returns the current snapshot of orders for a customer session so that the PWA can
 * implement a polling loop as a fallback when SSE is unavailable or disconnected (Req 2.3,
 * ADR-009).
 *
 * <p>This endpoint intentionally mirrors the behaviour of the SSE stream but returns a
 * one-shot HTTP response rather than a persistent connection. The client is responsible for
 * scheduling repeated calls at an appropriate interval (e.g. every 5 seconds for PWA, every
 * 10 seconds for the panel).
 *
 * <h2>Endpoint</h2>
 * <pre>GET /api/sessions/{sessionId}/orders/poll</pre>
 *
 * <p>The endpoint is public — no authentication is required — consistent with the other
 * customer session endpoints (Req 7.1 applies only to staff-panel endpoints).
 *
 * <h2>Response</h2>
 * A JSON array of order state objects for the session:
 * <pre>[{"orderId":"&lt;uuid&gt;","status":"&lt;LIFECYCLE_STATE&gt;"},...]</pre>
 *
 * <p>Returns an empty array when the session has no orders (Req 2.5).
 *
 * <p>The tenant context is derived from the session itself. Since the polling endpoint
 * serves the customer PWA (unauthenticated), the tenant is not available from the
 * security context. The session's {@link TenantId} is encoded in the session token
 * resolved upstream; for simplicity in the MVP the tenant context is populated by the
 * inbound filter from the session. Where no tenant context is present the repository
 * returns an empty list (safe, fail-closed).
 */
@RestController
public class OrderPollingController {

    private final OrderRepository orderRepository;

    public OrderPollingController(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository,
                "orderRepository must not be null");
    }

    /**
     * Returns the current order states for a customer session as a JSON snapshot
     * suitable for polling.
     *
     * <p>Tenant isolation is enforced by the repository's
     * {@link OrderRepository#findByTenantAndSession} method, which constrains results to
     * the resolved tenant (Req 10.2, 10.4). If the tenant context is not resolvable for
     * the current request the returned list is empty rather than an error (fail-closed for
     * unauthenticated customer requests).
     *
     * @param sessionId the customer session UUID from the path
     * @return list of {@link OrderStatusSnapshot}s for the session; empty when the session
     *         has no orders
     */
    @GetMapping("/api/sessions/{sessionId}/orders/poll")
    public List<OrderStatusSnapshot> pollOrders(@PathVariable("sessionId") UUID sessionId) {
        CustomerSessionId csid = new CustomerSessionId(sessionId);

        return TenantContextHolder.get()
                .map(tenantId -> orderRepository
                        .findByTenantAndSession(tenantId, csid)
                        .stream()
                        .map(order -> new OrderStatusSnapshot(
                                order.getId().value(),
                                order.getStatus().name()))
                        .toList())
                .orElseGet(List::of);
    }

    // -------------------------------------------------------------------------
    // Response shape
    // -------------------------------------------------------------------------

    /**
     * Snapshot of a single order's identity and current status.
     *
     * @param orderId the order's UUID
     * @param status  the current lifecycle state label (e.g. {@code "CREATED"})
     */
    public record OrderStatusSnapshot(UUID orderId, String status) {}
}

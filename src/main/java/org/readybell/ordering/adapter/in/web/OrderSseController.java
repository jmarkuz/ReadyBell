package org.readybell.ordering.adapter.in.web;

import org.readybell.ordering.adapter.in.web.sse.SseEmitterRegistry;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Objects;
import java.util.UUID;

/**
 * Inbound SSE adapter that pushes live order-state updates to PWA customers (Req 2.3, ≤5s)
 * and Restaurant Staff panel clients (Req 7.4, ≤10s).
 *
 * <p>Two streams are exposed:
 * <ul>
 *   <li>{@code GET /api/sessions/{sessionId}/orders/stream} — customer stream scoped to one
 *       session; emitters are keyed by {@link CustomerSessionId}.</li>
 *   <li>{@code GET /api/orders/stream} — staff-panel stream scoped to the authenticated
 *       tenant; emitters are keyed by {@link TenantId}.</li>
 * </ul>
 *
 * <p>Connections are kept in the {@link SseEmitterRegistry}; the
 * {@link org.readybell.ordering.adapter.in.web.sse.OrderStatusFanout} pushes each
 * {@code OrderStatusUpdated} event to them as a JSON payload
 * {@code {"orderId":"<uuid>","status":"<LIFECYCLE_STATE>"}} — directly, or through Redis pub/sub
 * when {@code readybell.sse.redis.enabled=true} so that all replicas serve all clients.
 */
@RestController
public class OrderSseController {

    private final SseEmitterRegistry registry;

    public OrderSseController(SseEmitterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /**
     * Opens an SSE stream for a customer session (Req 2.3, ≤5 s latency).
     *
     * <p>No authentication is required — the session ID in the path is the customer's
     * opaque handle to their own order list, consistent with the session-scoped public endpoints.
     *
     * @param sessionId the customer session UUID from the path
     * @return an {@link SseEmitter} without timeout; it stays open until the client disconnects
     */
    @GetMapping("/api/sessions/{sessionId}/orders/stream")
    public SseEmitter customerOrderStream(@PathVariable("sessionId") UUID sessionId) {
        return registry.registerCustomer(new CustomerSessionId(sessionId));
    }

    /**
     * Opens an SSE stream for the authenticated tenant's orders (Req 7.4, ≤10 s latency).
     *
     * @return an {@link SseEmitter} without timeout scoped to the current tenant
     */
    @GetMapping("/api/orders/stream")
    public SseEmitter staffOrderStream() {
        TenantId tenantId = TenantContextHolder.require();
        return registry.registerStaff(tenantId);
    }
}

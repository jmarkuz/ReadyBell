package org.readybell.ordering.adapter.in.web;

import org.readybell.ordering.domain.OrderRepository;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.tenancy.TenantContextHolder;
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
 * implement a polling loop as a fallback when SSE is unavailable or disconnected (,
 * ADR-009).
 */
@RestController
public class OrderPollingController {
    private final OrderRepository orderRepository;

    public OrderPollingController(OrderRepository orderRepository) {
        this.orderRepository = Objects.requireNonNull(orderRepository,
                "orderRepository must not be null");
    }

    /** Returns the current order states for a customer session as a JSON snapshot suitable for polling. */
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

    /** Snapshot of a single order's identity and current status. */
    public record OrderStatusSnapshot(UUID orderId, String status) {}
}

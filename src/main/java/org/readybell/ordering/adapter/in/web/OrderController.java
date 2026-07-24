package org.readybell.ordering.adapter.in.web;

import org.readybell.ordering.application.CreateOrderCommand;
import org.readybell.ordering.application.ManageOrderUseCase;
import org.readybell.ordering.application.TransitionOrderCommand;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Inbound REST adapter for the Order Management API (Restaurant Staff panel).
 *
 * <p>Exposes three endpoints:
 * <ul>
 *   <li>{@code POST  /api/orders}          — creates an order for a session (Req 4.1–4.6).</li>
 *   <li>{@code GET   /api/orders}          — lists all orders for the authenticated tenant (Req 7.3, 7.7).</li>
 *   <li>{@code PATCH /api/orders/{id}/status} — transitions order status (Req 5.1–5.8).</li>
 * </ul>
 *
 * <p>All endpoints resolve the current tenant via {@link TenantContextHolder#require()}.
 * Tenant isolation is enforced at the repository query layer (Req 10.2, 10.4). Cross-tenant
 * access is denied at both the application service and repository levels (Req 7.7, 10.3).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final ManageOrderUseCase manageOrderUseCase;
    private final OrderRepository orderRepository;

    public OrderController(ManageOrderUseCase manageOrderUseCase,
                           OrderRepository orderRepository) {
        this.manageOrderUseCase = Objects.requireNonNull(manageOrderUseCase,
                "manageOrderUseCase must not be null");
        this.orderRepository    = Objects.requireNonNull(orderRepository,
                "orderRepository must not be null");
    }

    /**
     * Creates a new order for the given customer session, scoped to the authenticated tenant.
     *
     * <p>The resolved tenant is stamped on the order (Req 4.2). Throws
     * {@link org.readybell.ordering.application.InvalidSessionException} if the session
     * does not exist or is inactive (Req 4.5), or
     * {@link org.readybell.ordering.application.CrossTenantAccessException} if the session
     * belongs to a different tenant (Req 4.6).
     *
     * @param request body containing the {@code sessionId}
     * @return 201 Created with the new order id and CREATED status
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@RequestBody CreateOrderRequest request) {
        TenantId tenantId = TenantContextHolder.require();
        CustomerSessionId sessionId = new CustomerSessionId(request.sessionId());
        Order order = manageOrderUseCase.createOrder(
                new CreateOrderCommand(tenantId, sessionId));
        return new OrderResponse(
                order.getId().value().toString(),
                order.getStatus().name());
    }

    /**
     * Lists all orders belonging to the authenticated tenant (Req 7.3, 7.7).
     *
     * <p>Returns an empty list when the tenant has no orders.
     *
     * @return 200 OK with the list of order summaries
     */
    @GetMapping
    public List<OrderResponse> listOrders() {
        TenantId tenantId = TenantContextHolder.require();
        return orderRepository.findAllByTenant(tenantId)
                .stream()
                .map(o -> new OrderResponse(
                        o.getId().value().toString(),
                        o.getStatus().name()))
                .toList();
    }

    /**
     * Transitions an order to a new status.
     *
     * <p>Enforces lifecycle invariants in the domain (Req 5.1–5.7). Returns the order
     * after the successful transition. Throws:
     * <ul>
     *   <li>{@link org.readybell.ordering.application.OrderNotFoundException} → 404 if the
     *       order does not exist for this tenant (Req 7.7, 10.3).</li>
     *   <li>{@link org.readybell.ordering.domain.InvalidTransitionException} → 422 for
     *       disallowed or self-transitions (Req 5.3, 5.4).</li>
     *   <li>{@link org.readybell.ordering.domain.TerminalStateException} → 422 for
     *       already-terminal orders (Req 5.7).</li>
     * </ul>
     *
     * @param id      the order UUID path variable
     * @param request body containing the target {@code status}
     * @return 200 OK with the updated order id and new status
     */
    @PatchMapping("/{id}/status")
    public OrderResponse transitionOrderStatus(@PathVariable("id") UUID id,
                                               @RequestBody TransitionStatusRequest request) {
        TenantId tenantId = TenantContextHolder.require();
        OrderId orderId = new OrderId(id);
        OrderStatus targetStatus = OrderStatus.valueOf(request.status());
        Order order = manageOrderUseCase.transitionOrderStatus(
                new TransitionOrderCommand(tenantId, orderId, targetStatus));
        return new OrderResponse(
                order.getId().value().toString(),
                order.getStatus().name());
    }

    // -------------------------------------------------------------------------
    // Request / Response records
    // -------------------------------------------------------------------------

    /**
     * Request body for {@code POST /api/orders}.
     *
     * @param sessionId the customer session UUID this order belongs to (Req 4.4)
     */
    public record CreateOrderRequest(UUID sessionId) {}

    /**
     * Request body for {@code PATCH /api/orders/{id}/status}.
     *
     * @param status the target {@link OrderStatus} name (e.g. "IN_PROGRESS")
     */
    public record TransitionStatusRequest(String status) {}

    /**
     * Response body for order creation and status transition.
     *
     * @param orderId the order identifier
     * @param status  the current order status label
     */
    public record OrderResponse(String orderId, String status) {}
}

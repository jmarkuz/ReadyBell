package org.readybell.ordering.adapter.in.web;

import jakarta.validation.Valid;

import org.readybell.ordering.application.CrossTenantAccessException;
import org.readybell.ordering.application.InvalidSessionException;
import org.readybell.ordering.application.OrderNotFoundException;
import org.readybell.ordering.application.CreateOrderCommand;
import org.readybell.ordering.application.ManageOrderUseCase;
import org.readybell.ordering.application.TransitionOrderCommand;
import org.readybell.ordering.domain.InvalidTransitionException;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.ordering.domain.TerminalStateException;
import org.readybell.platform.GlobalExceptionHandler;
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
 *   <li>{@code POST  /api/orders}             — creates an order for a session (Req 4.1–4.6).</li>
 *   <li>{@code GET   /api/orders}             — lists all orders for the authenticated tenant (Req 7.3, 7.7).</li>
 *   <li>{@code PATCH /api/orders/{id}/status} — transitions order status (Req 5.1–5.8).</li>
 * </ul>
 *
 * <p>All endpoints resolve the current tenant via {@link TenantContextHolder#require()}.
 * Tenant isolation is enforced at the repository query layer (Req 10.2, 10.4). Cross-tenant
 * access is denied at both the application service and repository levels (Req 7.7, 10.3).
 *
 * <p>Error responses are produced by {@link GlobalExceptionHandler}
 * as RFC 7807 {@code application/problem+json} bodies for:
 * <ul>
 *   <li>{@link InvalidSessionException} → 422 (Req 4.5)</li>
 *   <li>{@link CrossTenantAccessException} → 403 (Req 4.6, 7.7)</li>
 *   <li>{@link OrderNotFoundException} → 404 (Req 7.7, 10.3)</li>
 *   <li>{@link InvalidTransitionException} → 422 (Req 5.3, 5.4)</li>
 *   <li>{@link TerminalStateException} → 422 (Req 5.7)</li>
 * </ul>
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
     * {@link InvalidSessionException} if the session
     * does not exist or is inactive (Req 4.5), or
     * {@link CrossTenantAccessException} if the session
     * belongs to a different tenant (Req 4.6).
     *
     * @param request body containing the {@code sessionId}
     * @return 201 Created with the new order summary
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        TenantId tenantId = TenantContextHolder.require();
        CustomerSessionId sessionId = new CustomerSessionId(request.sessionId());
        var order = manageOrderUseCase.createOrder(
                new CreateOrderCommand(tenantId, sessionId));
        return OrderResponse.from(order);
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
                .map(OrderResponse::from)
                .toList();
    }

    /**
     * Transitions an order to a new status.
     *
     * <p>Enforces lifecycle invariants in the domain (Req 5.1–5.7). Returns the order
     * after the successful transition. Throws:
     * <ul>
     *   <li>{@link OrderNotFoundException} → 404 if the
     *       order does not exist for this tenant (Req 7.7, 10.3).</li>
     *   <li>{@link InvalidTransitionException} → 422 for
     *       disallowed or self-transitions (Req 5.3, 5.4).</li>
     *   <li>{@link TerminalStateException} → 422 for
     *       already-terminal orders (Req 5.7).</li>
     * </ul>
     *
     * @param id      the order UUID path variable
     * @param request body containing the target {@code status}
     * @return 200 OK with the updated order summary
     */
    @PatchMapping("/{id}/status")
    public OrderResponse transitionOrderStatus(@PathVariable("id") UUID id,
                                               @Valid @RequestBody TransitionOrderRequest request) {
        TenantId tenantId = TenantContextHolder.require();
        OrderId orderId = new OrderId(id);
        OrderStatus targetStatus = OrderStatus.valueOf(request.status());
        var order = manageOrderUseCase.transitionOrderStatus(
                new TransitionOrderCommand(tenantId, orderId, targetStatus));
        return OrderResponse.from(order);
    }
}

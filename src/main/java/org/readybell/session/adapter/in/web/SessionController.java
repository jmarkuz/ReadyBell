package org.readybell.session.adapter.in.web;

import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.session.application.StartSessionUseCase;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Inbound REST adapter for the Customer Session API.
 *
 * <p>Exposes two endpoints:
 * <ul>
 *   <li>{@code POST /api/sessions} — starts a new session from a QR token (Req 1.1, 1.3–1.7).</li>
 *   <li>{@code GET  /api/sessions/{id}/orders} — lists all orders for a session (Req 2.2, 2.5).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final StartSessionUseCase startSessionUseCase;
    private final OrderRepository orderRepository;

    public SessionController(StartSessionUseCase startSessionUseCase,
                             OrderRepository orderRepository) {
        this.startSessionUseCase = startSessionUseCase;
        this.orderRepository = orderRepository;
    }

    /**
     * Starts a new customer session identified by the QR-code token.
     *
     * @param request body containing the {@code qrToken}
     * @return 201 Created with {@code sessionId} and {@code tenantId}
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StartSessionResponse startSession(@RequestBody StartSessionRequest request) {
        CustomerSession session = startSessionUseCase.startSession(request.qrToken());
        return new StartSessionResponse(
                session.getId().value().toString(),
                session.getTenantId().value().toString()
        );
    }

    /**
     * Returns all orders associated with a session, scoped to the resolved tenant.
     *
     * <p>When the session has no orders the response includes {@code "empty": true}
     * to signal the absence explicitly (Req 2.5).
     *
     * @param id the session UUID path variable
     * @return 200 OK with the order list (may be empty)
     */
    @GetMapping("/{id}/orders")
    public OrderListResponse listOrders(@PathVariable("id") UUID id) {
        TenantId tenantId = TenantContextHolder.require();
        CustomerSessionId sessionId = new CustomerSessionId(id);

        List<Order> orders = orderRepository.findByTenantAndSession(tenantId, sessionId);

        List<OrderSummary> summaries = orders.stream()
                .map(o -> new OrderSummary(
                        o.getId().value().toString(),
                        o.getStatus().name()))
                .toList();

        return new OrderListResponse(summaries, summaries.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Request / Response records
    // -------------------------------------------------------------------------

    /**
     * Request body for {@code POST /api/sessions}.
     *
     * @param qrToken the QR-code payload identifying the restaurant (Req 1.7)
     */
    public record StartSessionRequest(String qrToken) {}

    /**
     * Response body for a successfully started session (Req 1.3).
     *
     * @param sessionId the newly created session identifier
     * @param tenantId  the restaurant (tenant) the session belongs to
     */
    public record StartSessionResponse(String sessionId, String tenantId) {}

    /**
     * A single order summary entry in the order-list response.
     *
     * @param orderId the order identifier
     * @param status  the current order status label
     */
    public record OrderSummary(String orderId, String status) {}

    /**
     * Response body for {@code GET /api/sessions/{id}/orders} (Req 2.2, 2.5).
     *
     * @param orders the list of order summaries (may be empty)
     * @param empty  {@code true} when the session has no orders
     */
    public record OrderListResponse(List<OrderSummary> orders, boolean empty) {}
}

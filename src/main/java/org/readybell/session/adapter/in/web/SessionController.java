package org.readybell.session.adapter.in.web;

import jakarta.validation.Valid;

import org.readybell.platform.GlobalExceptionHandler;
import org.readybell.restaurant.domain.DeactivatedRestaurantException;
import org.readybell.session.application.InvalidQrTokenException;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.session.application.StartSessionUseCase;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Inbound REST adapter for the Customer Session API.
 *
 * <p>Exposes two endpoints:
 * <ul>
 *   <li>{@code POST /api/sessions} — starts a new session from a QR token
 *       (Req 1.1, 1.3, 1.4, 1.5, 1.6, 1.7, 9.6).</li>
 *   <li>{@code GET  /api/sessions/{id}/orders} — lists all orders for a session
 *       with their current status; returns an empty list when the session has no
 *       orders (Req 2.2, 2.5).</li>
 * </ul>
 *
 * <p>Error responses use {@code application/problem+json} (RFC 7807) and are
 * produced by {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final StartSessionUseCase startSessionUseCase;
    private final OrderRepository orderRepository;
    private final CustomerSessionRepository sessionRepository;
    private final RestaurantRepository restaurantRepository;
    private final Clock clock;

    public SessionController(StartSessionUseCase startSessionUseCase,
                             OrderRepository orderRepository,
                             CustomerSessionRepository sessionRepository,
                             RestaurantRepository restaurantRepository,
                             Clock clock) {
        this.startSessionUseCase  = startSessionUseCase;
        this.orderRepository      = orderRepository;
        this.sessionRepository    = sessionRepository;
        this.restaurantRepository = restaurantRepository;
        this.clock                = clock;
    }

    /**
     * Data for the customer tracking page ({@code /track/{sessionId}}, reached from the receipt QR
     * code). Public: the unguessable session id is the capability. An unknown session id yields 404.
     */
    @GetMapping("/{id}/track")
    public TrackResponse track(@PathVariable("id") UUID id) {
        // the tenant is resolved from the session id; none resolved means the link is not valid
        TenantId tenantId = TenantContextHolder.get().orElseThrow(() -> new SessionUnknownException(id));
        var sessionId = new CustomerSessionId(id);
        var session = sessionRepository.findByTenantAndId(tenantId, sessionId)
                .orElseThrow(() -> new SessionUnknownException(id));
        String restaurantName = restaurantRepository.findByTenantId(tenantId)
                .map(r -> r.getConfig().name())
                .orElse("");
        var orders = orderRepository.findByTenantAndSession(tenantId, sessionId).stream()
                .sorted(Comparator.comparing(o -> o.getCreatedAt()))
                .map(o -> TrackResponse.TrackedOrder.from(o, clock))
                .toList();
        return new TrackResponse(restaurantName, session.isActive(), orders);
    }

    /**
     * Starts a new customer session identified by the QR-code token.
     *
     * <p>On success returns 201 Created with a {@link SessionResponse} body containing
     * the session id, status, and start timestamp (Req 1.1, 1.3, 1.6).
     *
     * <p>On failure the {@link GlobalExceptionHandler} maps:
     * <ul>
     *   <li>{@link InvalidQrTokenException} →
     *       422 Unprocessable Entity (Req 1.4, 1.7)</li>
     *   <li>{@link DeactivatedRestaurantException} →
     *       422 Unprocessable Entity (Req 9.6)</li>
     * </ul>
     *
     * @param request body containing the {@code qrToken}
     * @return 201 Created with a {@link SessionResponse}
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse startSession(@Valid @RequestBody StartSessionRequest request) {
        CustomerSession session = startSessionUseCase.startSession(request.qrToken());
        return SessionResponse.from(session);
    }

    /**
     * Returns all orders associated with the given session, scoped to the current tenant.
     *
     * <p>When the session has no orders the response is an empty list — callers detect
     * the absence by checking the list size (Req 2.5).
     *
     * @param id the session UUID path variable
     * @return 200 OK with the (possibly empty) list of {@link OrderStateResponse}
     */
    @GetMapping("/{id}/orders")
    public List<OrderStateResponse> listOrders(@PathVariable("id") UUID id) {
        TenantId tenantId = TenantContextHolder.require();
        CustomerSessionId sessionId = new CustomerSessionId(id);

        return orderRepository
                .findByTenantAndSession(tenantId, sessionId)
                .stream()
                .map(OrderStateResponse::from)
                .toList();
    }
}

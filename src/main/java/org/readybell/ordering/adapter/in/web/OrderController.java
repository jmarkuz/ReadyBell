package org.readybell.ordering.adapter.in.web;

import jakarta.validation.Valid;

import org.readybell.ordering.application.CrossTenantAccessException;
import org.readybell.ordering.application.InvalidSessionException;
import org.readybell.ordering.application.OrderNotFoundException;
import org.readybell.ordering.application.CreateOrderCommand;
import org.readybell.ordering.application.ManageOrderUseCase;
import org.readybell.ordering.application.TransitionOrderCommand;
import org.readybell.ordering.domain.InvalidTransitionException;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderQuery;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.ordering.domain.TerminalStateException;
import org.readybell.platform.GlobalExceptionHandler;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.UUID;

/**
 * Inbound REST adapter for the Order Management API (Restaurant Staff panel).
 *
 * <p>Exposes these endpoints:
 * <ul>
 *   <li>{@code POST  /api/orders}             — creates an order for a session.</li>
 *   <li>{@code GET   /api/orders}             — paginated, filterable, sortable list.</li>
 *   <li>{@code GET   /api/orders/metrics}     — order counts per status for the dashboard cards.</li>
 *   <li>{@code GET   /api/orders/poll}        — polling fallback with ETag.</li>
 *   <li>{@code PATCH /api/orders/{id}/status} — transitions order status.</li>
 * </ul>
 *
 * <p>All endpoints resolve the current tenant via {@link TenantContextHolder#require()}.
 * Tenant isolation is enforced at the repository query layer. Cross-tenant
 * access is denied at both the application service and repository levels.
 *
 * <p>Error responses are produced by {@link GlobalExceptionHandler}
 * as RFC 7807 {@code application/problem+json} bodies for:
 * <ul>
 *   <li>{@link InvalidSessionException} → 422</li>
 *   <li>{@link CrossTenantAccessException} → 403</li>
 *   <li>{@link OrderNotFoundException} → 404</li>
 *   <li>{@link InvalidTransitionException} → 422</li>
 *   <li>{@link TerminalStateException} → 422</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final ManageOrderUseCase manageOrderUseCase;
    private static final int POLL_LIMIT = 200;

    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderController(ManageOrderUseCase manageOrderUseCase,
                           OrderRepository orderRepository,
                           Clock clock) {
        this.manageOrderUseCase = Objects.requireNonNull(manageOrderUseCase,
                "manageOrderUseCase must not be null");
        this.orderRepository    = Objects.requireNonNull(orderRepository,
                "orderRepository must not be null");
        this.clock              = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Creates a new order for the given customer session, scoped to the authenticated tenant.
     *
     * <p>The resolved tenant is stamped on the order. Optional customer name, items and
     * ETA are stored with the order. Throws {@link InvalidSessionException} if the session
     * does not exist or is inactive, or {@link CrossTenantAccessException} if the
     * session belongs to a different tenant.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        TenantId tenantId = TenantContextHolder.require();
        var order = request.sessionId() == null
                ? manageOrderUseCase.createOrderWithNewSession(tenantId, request.toDetails(clock))
                : manageOrderUseCase.createOrder(new CreateOrderCommand(
                        tenantId, new CustomerSessionId(request.sessionId()), request.toDetails(clock)));
        return OrderResponse.from(order);
    }

    /** Lists the authenticated tenant's orders: paginated, filterable, sortable, searchable. */
    @GetMapping
    public OrderPageResponse listOrders(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "pageSize", defaultValue = "" + OrderQuery.DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(name = "status", required = false) List<String> status,
            @RequestParam(name = "sortBy", defaultValue = "created") String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = "desc") String sortOrder,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "from", required = false) Instant from,
            @RequestParam(name = "to", required = false) Instant to) {
        TenantId tenantId = TenantContextHolder.require();
        Set<OrderStatus> statuses = status == null ? Set.of() : status.stream()
                .map(s -> OrderStatus.valueOf(s.strip().toUpperCase(Locale.ROOT)))
                .collect(Collectors.toSet());
        var result = orderRepository.findPage(tenantId,
                new OrderQuery(page, pageSize, statuses, parseSort(sortBy), parseDirection(sortOrder), search, from, to));
        return new OrderPageResponse(
                result.items().stream().map(o -> OrderRowResponse.from(o, clock)).toList(),
                result.totalCount(), result.pageCount(), result.page());
    }

    /**
     * Order counts per status for the dashboard cards: always the tenant's real totals, independent
     * of any list filter or page.
     */
    @GetMapping("/metrics")
    public OrderMetricsResponse metrics() {
        var counts = orderRepository.countByStatus(TenantContextHolder.require());
        return new OrderMetricsResponse(counts.total(), counts.count(OrderStatus.CREATED),
                counts.count(OrderStatus.IN_PROGRESS), counts.count(OrderStatus.READY),
                counts.count(OrderStatus.COMPLETED), counts.count(OrderStatus.CANCELLED));
    }

    public record OrderMetricsResponse(long totalOrders, long created, long inProgress, long ready,
                                       long completed, long cancelled) {}

    /**
     * Polling fallback for the SSE stream: orders changed since {@code since} (default: most
     * recent), with an ETag over their ids and versions.
     */
    @GetMapping("/poll")
    public ResponseEntity<OrderPollResponse> pollOrders(
            @RequestParam(name = "since", required = false) Instant since,
            @RequestParam(name = "eTag", required = false) String eTag) {
        TenantId tenantId = TenantContextHolder.require();
        List<Order> orders = orderRepository.findUpdatedSince(tenantId, since, POLL_LIMIT);
        String current = etagOf(orders);
        if (eTag != null && normalize(eTag).equals(normalize(current))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(current).build();
        }
        return ResponseEntity.ok().eTag(current).body(new OrderPollResponse(
                orders.stream().map(o -> OrderRowResponse.from(o, clock)).toList(), current));
    }

    public record OrderPollResponse(List<OrderRowResponse> orders, String eTag) {}

    private static OrderQuery.Sort parseSort(String sortBy) {
        return switch (sortBy) {
            case "created" -> OrderQuery.Sort.CREATED;
            case "eta" -> OrderQuery.Sort.ETA;
            case "status" -> OrderQuery.Sort.STATUS;
            case "customerName" -> OrderQuery.Sort.CUSTOMER_NAME;
            case "orderId" -> OrderQuery.Sort.ORDER_ID;
            default -> throw new IllegalArgumentException("sortBy must be one of created, eta, status, customerName, orderId");
        };
    }

    private static boolean parseDirection(String sortOrder) {
        return switch (sortOrder.toLowerCase(Locale.ROOT)) {
            case "asc" -> true;
            case "desc" -> false;
            default -> throw new IllegalArgumentException("sortOrder must be asc or desc");
        };
    }

    private static String etagOf(List<Order> orders) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Order o : orders) {
                md.update((o.getId().value() + ":" + o.getVersion() + ";").getBytes(StandardCharsets.UTF_8));
            }
            return "\"" + HexFormat.of().formatHex(md.digest(), 0, 8) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalize(String etag) {
        String t = etag.strip();
        if (t.startsWith("W/")) {
            t = t.substring(2);
        }
        return t.replace("\"", "");
    }

    /**
     * Transitions an order to a new status.
     *
     * <p>Enforces lifecycle invariants in the domain. Returns the order
     * after the successful transition. Throws:
     * <ul>
     *   <li>{@link OrderNotFoundException} → 404 if the
     *       order does not exist for this tenant.</li>
     *   <li>{@link InvalidTransitionException} → 422 for
     *       disallowed or self-transitions.</li>
     *   <li>{@link TerminalStateException} → 422 for
     *       already-terminal orders.</li>
     * </ul>
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

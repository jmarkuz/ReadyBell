package org.readybell.platform.devdata;

import org.readybell.ordering.adapter.out.persistence.OrderJpaEntity;
import org.readybell.ordering.adapter.out.persistence.OrderJpaRepository;
import org.readybell.ordering.application.CreateOrderCommand;
import org.readybell.ordering.application.ManageOrderUseCase;
import org.readybell.ordering.application.TransitionOrderCommand;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderDetails;
import org.readybell.ordering.domain.OrderItem;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.session.adapter.out.persistence.CustomerSessionJpaRepository;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Development helper: fills the caller's restaurant with generated orders so the dashboard can be
 * exercised without a real customer flow, and removes them again.
 *
 * <p>Only registered under the {@code dev} profile. Generated orders are recognised by the
 * {@link #MARKER} suffix on the customer name; nothing else is ever deleted.
 */
@RestController
@RequestMapping("/api/dev/test-data")
@Profile("dev")
public class DevTestDataController {

    /** Suffix that tags generated customer names. */
    static final String MARKER = " (test)";

    private static final int MAX_COUNT = 50;
    static final int MAX_IMPORT = 500;

    private static final List<String> CUSTOMERS = List.of(
            "Olena K.", "Taras M.", "Iryna S.", "Andrii P.", "Maria L.", "Dmytro B.", "Sofia H.", "Yurii V.");
    private static final List<String> DISHES = List.of(
            "Latte", "Cappuccino", "Espresso", "Flat white", "Croissant", "Cheesecake",
            "Avocado toast", "Caesar salad", "Margherita pizza", "Borscht", "Lemonade", "Green tea");
    /** Target status mix: mostly active orders, some finished. */
    private static final List<OrderStatus> STATUS_MIX = List.of(
            OrderStatus.CREATED, OrderStatus.CREATED, OrderStatus.IN_PROGRESS, OrderStatus.IN_PROGRESS,
            OrderStatus.READY, OrderStatus.READY, OrderStatus.COMPLETED, OrderStatus.CANCELLED);

    private final ManageOrderUseCase manageOrders;
    private final CustomerSessionRepository sessions;
    private final OrderJpaRepository orderJpa;
    private final CustomerSessionJpaRepository sessionJpa;
    private final Clock clock;

    public DevTestDataController(ManageOrderUseCase manageOrders, CustomerSessionRepository sessions,
                                 OrderJpaRepository orderJpa, CustomerSessionJpaRepository sessionJpa, Clock clock) {
        this.manageOrders = manageOrders;
        this.sessions = sessions;
        this.orderJpa = orderJpa;
        this.sessionJpa = sessionJpa;
        this.clock = clock;
    }

    /** Creates {@code count} orders (default 12) in assorted statuses for the caller's restaurant. */
    @PostMapping
    public Map<String, Integer> generate(@RequestParam(defaultValue = "12") int count) {
        TenantId tenantId = TenantContextHolder.require();
        int n = Math.max(1, Math.min(count, MAX_COUNT));
        var rnd = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            List<OrderItem> items = new ArrayList<>();
            for (int k = rnd.nextInt(1, 4); k > 0; k--) {
                items.add(new OrderItem(DISHES.get(rnd.nextInt(DISHES.size())), rnd.nextInt(1, 4)));
            }
            createOrder(tenantId,
                    CUSTOMERS.get(rnd.nextInt(CUSTOMERS.size())),
                    items,
                    rnd.nextInt(5, 45),
                    STATUS_MIX.get(rnd.nextInt(STATUS_MIX.size())));
        }
        return Map.of("created", n);
    }

    /**
     * Imports orders from a file the dashboard has parsed ({@code {"orders": [...]}}). Each record
     * becomes one order, driven to its {@code status} through the normal transitions.
     */
    @PostMapping("/import")
    public Map<String, Integer> importOrders(@Valid @RequestBody ImportRequest request) {
        TenantId tenantId = TenantContextHolder.require();
        for (ImportOrder o : request.orders()) {
            List<OrderItem> items = o.items() == null ? List.of()
                    : o.items().stream().map(i -> new OrderItem(i.name(), i.quantity())).toList();
            createOrder(tenantId, o.customerName(), items, o.estimatedMinutes(),
                    o.status() == null ? OrderStatus.CREATED : o.status());
        }
        return Map.of("created", request.orders().size());
    }

    public record ImportRequest(@NotEmpty @Size(max = MAX_IMPORT) List<@Valid @NotNull ImportOrder> orders) {}

    public record ImportOrder(
            @Size(max = OrderDetails.MAX_CUSTOMER_NAME - 7) String customerName,
            @Min(0) @Max(24 * 60) Integer estimatedMinutes,
            OrderStatus status,
            @Size(max = OrderDetails.MAX_ITEMS) List<@Valid @NotNull Item> items) {}

    public record Item(@NotBlank @Size(max = OrderItem.MAX_NAME_LENGTH) String name,
                       @Min(1) @Max(OrderItem.MAX_QUANTITY) int quantity) {}

    private void createOrder(TenantId tenantId, String customerName, List<OrderItem> items,
                             Integer estimatedMinutes, OrderStatus target) {
        CustomerSession session = CustomerSession.start(tenantId, clock);
        sessions.save(session);
        String name = (customerName == null || customerName.isBlank() ? "Guest" : customerName.strip()) + MARKER;
        var details = new OrderDetails(name, items,
                estimatedMinutes == null ? null : clock.instant().plus(Duration.ofMinutes(estimatedMinutes)));
        Order order = manageOrders.createOrder(new CreateOrderCommand(tenantId, session.getId(), details));
        for (OrderStatus step : path(target)) {
            manageOrders.transitionOrderStatus(new TransitionOrderCommand(tenantId, order.getId(), step));
        }
    }

    /** Deletes every generated order (and its session) of the caller's restaurant. */
    @DeleteMapping
    @Transactional
    public Map<String, Integer> clear() {
        TenantId tenantId = TenantContextHolder.require();
        List<OrderJpaEntity> generated = orderJpa.findByTenantId(tenantId.value()).stream()
                .filter(o -> o.getCustomerName() != null && o.getCustomerName().endsWith(MARKER))
                .toList();
        orderJpa.deleteAll(generated);
        sessionJpa.deleteAllById(generated.stream().map(OrderJpaEntity::getSessionId).distinct().toList());
        return Map.of("deleted", generated.size());
    }

    /** Transitions needed to reach {@code target} from CREATED. */
    private static List<OrderStatus> path(OrderStatus target) {
        return switch (target) {
            case CREATED -> List.of();
            case IN_PROGRESS -> List.of(OrderStatus.IN_PROGRESS);
            case READY -> List.of(OrderStatus.IN_PROGRESS, OrderStatus.READY);
            case COMPLETED -> List.of(OrderStatus.IN_PROGRESS, OrderStatus.READY, OrderStatus.COMPLETED);
            case CANCELLED -> List.of(OrderStatus.CANCELLED);
        };
    }
}

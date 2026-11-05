package org.readybell.ordering.application;

import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderNumberGenerator;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.DomainEventPublisher;

import java.time.Clock;
import org.readybell.ordering.domain.OrderDetails;
import org.readybell.session.domain.CustomerSession;
import org.readybell.shared.domain.TenantId;
import java.util.Objects;

/**
 * Application service implementing {@link ManageOrderUseCase}.
 *
 * <p>This class is plain Java — no Spring annotations. Constructor injection provides
 * every dependency; wiring happens in the composition root (Spring {@code @Configuration}
 * or equivalent). The domain and adapter layers are referenced only through their port
 * interfaces, preserving the Clean Architecture dependency rule.
 *
 * <p>Task 11.1 implements {@link #createOrder(CreateOrderCommand)}.
 * Task 11.3 implements {@link #transitionOrderStatus(TransitionOrderCommand)}.
 *
 * <p>Requirements covered: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 5.1–5.8.
 */
public class ManageOrderService implements ManageOrderUseCase {

    private final CustomerSessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final OrderNumberGenerator orderNumbers;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * @param sessionRepository repository port for loading and validating the target
     *                          customer session
     * @param orderRepository   repository port for persisting the new order
     * @param orderNumbers      port allocating the restaurant's next sequential order number
     * @param eventPublisher    outbound port for publishing domain events
     * @param clock             clock used to timestamp the order and its events;
     *                          injectable for deterministic testing
     */
    public ManageOrderService(CustomerSessionRepository sessionRepository,
                              OrderRepository orderRepository,
                              OrderNumberGenerator orderNumbers,
                              DomainEventPublisher eventPublisher,
                              Clock clock) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository,
                "sessionRepository must not be null");
        this.orderRepository   = Objects.requireNonNull(orderRepository,
                "orderRepository must not be null");
        this.orderNumbers      = Objects.requireNonNull(orderNumbers,
                "orderNumbers must not be null");
        this.eventPublisher    = Objects.requireNonNull(eventPublisher,
                "eventPublisher must not be null");
        this.clock             = Objects.requireNonNull(clock,
                "clock must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation steps:
     * <ol>
     *   <li>Load the session using the staff's tenant id so cross-tenant sessions are
     *       excluded by the repository layer (Req 10.2). An empty result means the session
     *       does not exist OR belongs to another tenant — both surface as
     *       {@link InvalidSessionException} (Req 4.5).</li>
     *   <li>If the session is not active → {@link InvalidSessionException} (Req 4.5).</li>
     *   <li>Defence-in-depth cross-tenant guard (Req 4.6): if the loaded session's
     *       tenant differs from the command tenant, throw
     *       {@link CrossTenantAccessException}.</li>
     *   <li>Create the Order via {@link Order#create} (Req 4.1, 4.2, 4.4).</li>
     *   <li>Persist via {@link OrderRepository#save}.</li>
     *   <li>Publish pending events (Req 4.3).</li>
     *   <li>Clear pending events on the aggregate.</li>
     *   <li>Return the order.</li>
     * </ol>
     */
    @Override
    public Order createOrder(CreateOrderCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        // Step 1 — load session scoped to the staff's tenant (Req 10.2)
        var session = sessionRepository
                .findByTenantAndId(command.tenantId(), command.sessionId())
                .orElseThrow(() -> new InvalidSessionException(command.sessionId())); // Req 4.5

        // Step 2 — ensure the session is active (Req 4.5)
        if (!session.isActive()) {
            throw new InvalidSessionException(command.sessionId());
        }

        // Step 3 — defence-in-depth cross-tenant guard (Req 4.6)
        if (!session.getTenantId().equals(command.tenantId())) {
            throw new CrossTenantAccessException(command.tenantId(), command.sessionId());
        }

        // Step 4 — create the order (Req 4.1, 4.2, 4.4)
        var order = Order.create(command.tenantId(), command.sessionId(), clock, command.details(),
                orderNumbers.next(command.tenantId()));

        // Step 5 — persist
        orderRepository.save(order);

        // Step 6 — publish pending domain events (Req 4.3)
        eventPublisher.publishAll(order.getPendingEvents());

        // Step 7 — clear pending events
        order.clearPendingEvents();

        // Step 8 — return
        return order;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation steps:
     * <ol>
     *   <li>Load the order scoped to the command's {@code tenantId} (Req 10.2, 10.4);
     *       if absent throw {@link OrderNotFoundException}.</li>
     *   <li>Apply {@link Order#transitionTo} — domain invariants are enforced there
     *       (Req 5.1–5.7).</li>
     *   <li>Persist via {@link OrderRepository#save}; the persistence adapter enforces
     *       optimistic locking on the {@code version} field so at most one concurrent
     *       transition wins (Req 5.8).</li>
     *   <li>Publish all pending events ({@code OrderStatusUpdated} always;
     *       {@code OrderReady} additionally when target is READY) (Req 5.5, 5.6).</li>
     *   <li>Clear pending events on the aggregate.</li>
     *   <li>Return the updated {@link Order}.</li>
     * </ol>
     */
    @Override
    public Order createOrderWithNewSession(TenantId tenantId, OrderDetails details) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        var session = CustomerSession.start(tenantId, clock);
        sessionRepository.save(session);
        eventPublisher.publishAll(session.getPendingEvents());
        return createOrder(new CreateOrderCommand(tenantId, session.getId(), details));
    }

    @Override
    public Order transitionOrderStatus(TransitionOrderCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        // Step 1 — load order scoped to the requesting tenant (Req 10.2, 10.4)
        var order = orderRepository
                .findByTenantAndId(command.tenantId(), command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.tenantId(), command.orderId()));

        // Step 2 — apply transition; domain enforces invariants (Req 5.1–5.7)
        order.transitionTo(command.targetStatus(), clock);

        // Step 3 — persist under optimistic locking (Req 5.8)
        orderRepository.save(order);

        // Step 4 — publish pending events (Req 5.5, 5.6)
        eventPublisher.publishAll(order.getPendingEvents());

        // Step 5 — clear pending events
        order.clearPendingEvents();

        // Step 6 — return
        return order;
    }
}

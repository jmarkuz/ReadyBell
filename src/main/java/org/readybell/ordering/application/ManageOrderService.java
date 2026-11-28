package org.readybell.ordering.application;

import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderNumberGenerator;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.ordering.domain.OrderTimelineRepository;
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
 */
public class ManageOrderService implements ManageOrderUseCase {
    private final CustomerSessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final OrderNumberGenerator orderNumbers;
    private final OrderTimelineRepository timeline;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public ManageOrderService(CustomerSessionRepository sessionRepository,
                              OrderRepository orderRepository,
                              OrderNumberGenerator orderNumbers,
                              OrderTimelineRepository timeline,
                              DomainEventPublisher eventPublisher,
                              Clock clock) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository,
                "sessionRepository must not be null");
        this.orderRepository   = Objects.requireNonNull(orderRepository,
                "orderRepository must not be null");
        this.orderNumbers      = Objects.requireNonNull(orderNumbers,
                "orderNumbers must not be null");
        this.timeline          = Objects.requireNonNull(timeline,
                "timeline must not be null");
        this.eventPublisher    = Objects.requireNonNull(eventPublisher,
                "eventPublisher must not be null");
        this.clock             = Objects.requireNonNull(clock,
                "clock must not be null");
    }

    @Override
    public Order createOrder(CreateOrderCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        var session = sessionRepository
                .findByTenantAndId(command.tenantId(), command.sessionId())
                .orElseThrow(() -> new InvalidSessionException(command.sessionId()));

        if (!session.isActive()) {
            throw new InvalidSessionException(command.sessionId());
        }

        if (!session.getTenantId().equals(command.tenantId())) {
            throw new CrossTenantAccessException(command.tenantId(), command.sessionId());
        }

        var order = Order.create(command.tenantId(), command.sessionId(), clock, command.details(),
                orderNumbers.next(command.tenantId()));

        orderRepository.save(order);
        timeline.recordTransition(order, null, order.getCreatedAt());

        eventPublisher.publishAll(order.getPendingEvents());

        order.clearPendingEvents();

        return order;
    }

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

        var order = orderRepository
                .findByTenantAndId(command.tenantId(), command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.tenantId(), command.orderId()));

        var from = order.getStatus();
        order.transitionTo(command.targetStatus(), clock);

        // persist under optimistic locking, then record the change and, when it ends, the fact row
        orderRepository.save(order);
        var at = clock.instant();
        timeline.recordTransition(order, from, at);
        if (order.getStatus().isTerminal()) {
            timeline.recordFinished(order, at);
        }

        eventPublisher.publishAll(order.getPendingEvents());

        order.clearPendingEvents();

        return order;
    }
}

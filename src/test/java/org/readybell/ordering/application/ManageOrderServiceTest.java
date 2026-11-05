package org.readybell.ordering.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.ordering.domain.InMemoryOrderRepository;
import org.readybell.ordering.domain.InvalidTransitionException;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderDetails;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderReady;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.ordering.domain.OrderStatusUpdated;
import org.readybell.ordering.domain.TerminalStateException;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.session.domain.InMemoryCustomerSessionRepository;
import org.readybell.session.domain.SessionStatus;
import org.readybell.shared.domain.RecordingDomainEventPublisher;
import org.readybell.shared.domain.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManageOrderServiceTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final TenantId OTHER_TENANT = new TenantId(UUID.randomUUID());
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

    private InMemoryCustomerSessionRepository sessions;
    private InMemoryOrderRepository orders;
    private RecordingDomainEventPublisher events;
    private ManageOrderService service;

    @BeforeEach
    void setUp() {
        sessions = new InMemoryCustomerSessionRepository();
        orders = new InMemoryOrderRepository();
        events = new RecordingDomainEventPublisher();
        service = new ManageOrderService(sessions, orders, events, CLOCK);
    }

    private CustomerSession activeSession(TenantId tenant) {
        var session = CustomerSession.start(tenant, CLOCK);
        session.clearPendingEvents();
        sessions.save(session);
        return session;
    }

    // ---- createOrder ------------------------------------------------------------------------

    @Test
    void createOrderPersistsTheOrderForTheSession() {
        var session = activeSession(TENANT);

        Order order = service.createOrder(new CreateOrderCommand(TENANT, session.getId()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTenantId()).isEqualTo(TENANT);
        assertThat(order.getSessionId()).isEqualTo(session.getId());
        assertThat(orders.findByTenantAndId(TENANT, order.getId())).isPresent();
        assertThat(order.getPendingEvents()).isEmpty();                       // published, then cleared
    }

    @Test
    void createOrderKeepsTheStaffDetails() {
        var session = activeSession(TENANT);
        var estimate = CLOCK.instant().plusSeconds(600);
        var details = new OrderDetails("Anna", java.util.List.of(new org.readybell.ordering.domain.OrderItem("Burger", 2)), estimate);

        Order order = service.createOrder(new CreateOrderCommand(TENANT, session.getId(), details));

        assertThat(order.getCustomerName()).isEqualTo("Anna");
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getEstimatedReadyAt()).isEqualTo(estimate);
    }

    @Test
    void createOrderRejectsAnUnknownSession() {
        var unknown = new CustomerSessionId(UUID.randomUUID());

        assertThatThrownBy(() -> service.createOrder(new CreateOrderCommand(TENANT, unknown)))
                .isInstanceOf(InvalidSessionException.class);
        assertThat(orders.size()).isZero();
    }

    @Test
    void createOrderRejectsAnEndedSession() {
        var ended = CustomerSession.reconstitute(new CustomerSessionId(UUID.randomUUID()), TENANT,
                SessionStatus.ENDED, CLOCK.instant());
        sessions.save(ended);

        assertThatThrownBy(() -> service.createOrder(new CreateOrderCommand(TENANT, ended.getId())))
                .isInstanceOf(InvalidSessionException.class);
        assertThat(orders.size()).isZero();
    }

    @Test
    void createOrderCannotUseAnotherTenantsSession() {
        var session = activeSession(OTHER_TENANT);

        assertThatThrownBy(() -> service.createOrder(new CreateOrderCommand(TENANT, session.getId())))
                .isInstanceOf(InvalidSessionException.class);
        assertThat(orders.size()).isZero();
    }

    @Test
    void createOrderRejectsNullCommand() {
        assertThatThrownBy(() -> service.createOrder(null)).isInstanceOf(NullPointerException.class);
    }

    // ---- createOrderWithNewSession ----------------------------------------------------------

    @Test
    void createOrderWithNewSessionStartsASessionAndAnOrder() {
        Order order = service.createOrderWithNewSession(TENANT, OrderDetails.NONE);

        var session = sessions.findByTenantAndId(TENANT, order.getSessionId());
        assertThat(session).isPresent();
        assertThat(session.get().isActive()).isTrue();
        assertThat(order.getTenantId()).isEqualTo(TENANT);
        assertThat(events.hasNoPublishedEvents()).isFalse();                   // session-started event published
    }

    @Test
    void createOrderWithNewSessionRejectsNullTenant() {
        assertThatThrownBy(() -> service.createOrderWithNewSession(null, OrderDetails.NONE))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- transitionOrderStatus --------------------------------------------------------------

    private Order existingOrder() {
        return service.createOrder(new CreateOrderCommand(TENANT, activeSession(TENANT).getId()));
    }

    @Test
    void transitionMovesTheOrderAndPublishesStatusUpdated() {
        Order order = existingOrder();
        events.reset();

        Order updated = service.transitionOrderStatus(
                new TransitionOrderCommand(TENANT, order.getId(), OrderStatus.IN_PROGRESS));

        assertThat(updated.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(orders.findByTenantAndId(TENANT, order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.IN_PROGRESS);
        var published = events.publishedEventsOfType(OrderStatusUpdated.class);
        assertThat(published).hasSize(1);
        assertThat(published.get(0).fromStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(published.get(0).toStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
        assertThat(events.publishedEventsOfType(OrderReady.class)).isEmpty();
        assertThat(updated.getPendingEvents()).isEmpty();
    }

    @Test
    void transitionToReadyAlsoPublishesOrderReady() {
        Order order = existingOrder();
        service.transitionOrderStatus(new TransitionOrderCommand(TENANT, order.getId(), OrderStatus.IN_PROGRESS));
        events.reset();

        service.transitionOrderStatus(new TransitionOrderCommand(TENANT, order.getId(), OrderStatus.READY));

        assertThat(events.publishedEventsOfType(OrderReady.class)).hasSize(1);
        assertThat(events.publishedEventsOfType(OrderStatusUpdated.class)).hasSize(1);
    }

    @Test
    void transitionOfAnUnknownOrderIsNotFound() {
        var command = new TransitionOrderCommand(TENANT, OrderId.newId(), OrderStatus.IN_PROGRESS);

        assertThatThrownBy(() -> service.transitionOrderStatus(command)).isInstanceOf(OrderNotFoundException.class);
        assertThat(events.hasNoPublishedEvents()).isTrue();
    }

    @Test
    void transitionCannotTouchAnotherTenantsOrder() {
        Order order = existingOrder();

        assertThatThrownBy(() -> service.transitionOrderStatus(
                new TransitionOrderCommand(OTHER_TENANT, order.getId(), OrderStatus.IN_PROGRESS)))
                .isInstanceOf(OrderNotFoundException.class);
        assertThat(orders.findByTenantAndId(TENANT, order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void invalidTransitionIsRejectedAndNothingIsPublished() {
        Order order = existingOrder();
        events.reset();

        assertThatThrownBy(() -> service.transitionOrderStatus(
                new TransitionOrderCommand(TENANT, order.getId(), OrderStatus.READY)))
                .isInstanceOf(InvalidTransitionException.class);
        assertThat(events.hasNoPublishedEvents()).isTrue();
    }

    @Test
    void finishedOrderCannotChangeAnymore() {
        Order order = existingOrder();
        service.transitionOrderStatus(new TransitionOrderCommand(TENANT, order.getId(), OrderStatus.CANCELLED));

        assertThatThrownBy(() -> service.transitionOrderStatus(
                new TransitionOrderCommand(TENANT, order.getId(), OrderStatus.IN_PROGRESS)))
                .isInstanceOf(TerminalStateException.class);
    }

    @Test
    void transitionRejectsNullCommand() {
        assertThatThrownBy(() -> service.transitionOrderStatus(null)).isInstanceOf(NullPointerException.class);
    }
}

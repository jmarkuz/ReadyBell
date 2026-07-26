package org.readybell.ordering.domain;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.TenantId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based and unit tests for {@link Order} aggregate.
 *
 * <p>Validates that:
 * - Property 10: A newly created order is well-formed
 * - Property 11: OrderCreated is published on creation
 * - Property 15: State changes and READY publish the correct events
 */
class OrderPropertyTests {

    private static final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-07-26T12:00:00Z"),
            ZoneId.of("UTC"));

    /**
     * Property 10: A newly created order is well-formed
     */
    @Property(tries = 100)
    @org.junit.jupiter.api.Tag("restaurant-notification-platform")
    @org.junit.jupiter.api.Tag("property-10")
    @org.junit.jupiter.api.Tag("A newly created order is well-formed")
    void newlyCreatedOrderIsWellFormed() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        CustomerSessionId sessionId = CustomerSessionId.newId();

        Order order = Order.create(tenantId, sessionId, fixedClock);

        assertNotNull(order.getId());
        assertNotNull(order.getId().value());
        assertEquals(OrderStatus.CREATED, order.getStatus());
        assertEquals(0L, order.getVersion());
        assertEquals(tenantId, order.getTenantId());
        assertEquals(sessionId, order.getSessionId());
    }

    /**
     * Property 11: OrderCreated is published on creation
     */
    @Property(tries = 100)
    @org.junit.jupiter.api.Tag("restaurant-notification-platform")
    @org.junit.jupiter.api.Tag("property-11")
    @org.junit.jupiter.api.Tag("OrderCreated is published on creation")
    void orderCreatedEventIsPublishedOnCreation() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        CustomerSessionId sessionId = CustomerSessionId.newId();

        Order order = Order.create(tenantId, sessionId, fixedClock);
        List<DomainEvent> events = order.getPendingEvents();

        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof OrderCreated);

        OrderCreated created = (OrderCreated) events.get(0);
        assertEquals(order.getId(), created.orderId());
        assertEquals(tenantId, created.tenantId());
        assertEquals(sessionId, created.sessionId());
    }

    /**
     * Property 15: State changes publish the correct events
     */
    @Property(tries = 100)
    @org.junit.jupiter.api.Tag("restaurant-notification-platform")
    @org.junit.jupiter.api.Tag("property-15")
    @org.junit.jupiter.api.Tag("State changes and READY publish the correct events")
    void stateChangesAndReadyPublishCorrectEvents(
            @ForAll("allowedTransition") AllowedTransition transition) {

        TenantId tenantId = new TenantId(UUID.randomUUID());
        CustomerSessionId sessionId = CustomerSessionId.newId();
        Order order = Order.create(tenantId, sessionId, fixedClock);

        advanceOrderToState(order, transition.source());
        int previousEventCount = order.getPendingEvents().size();

        order.transitionTo(transition.target(), fixedClock);

        List<DomainEvent> events = order.getPendingEvents();
        List<DomainEvent> newEvents = events.subList(previousEventCount, events.size());

        assertEquals(transition.target(), order.getStatus());

        OrderStatusUpdated statusUpdated = null;
        OrderReady readyEvent = null;

        for (DomainEvent e : newEvents) {
            if (e instanceof OrderStatusUpdated u) {
                statusUpdated = u;
            } else if (e instanceof OrderReady r) {
                readyEvent = r;
            }
        }

        assertNotNull(statusUpdated);
        assertEquals(transition.source(), statusUpdated.fromStatus());
        assertEquals(transition.target(), statusUpdated.toStatus());

        if (transition.target() == OrderStatus.READY) {
            assertNotNull(readyEvent);
            assertEquals(order.getId(), readyEvent.orderId());
        }
    }

    @Test
    void orderCreationWithNullTenantRaisesNpe() {
        CustomerSessionId sessionId = CustomerSessionId.newId();
        assertThrows(NullPointerException.class,
                () -> Order.create(null, sessionId, fixedClock));
    }

    @Test
    void orderCreationWithNullSessionRaisesNpe() {
        TenantId tenantId = new TenantId(UUID.randomUUID());
        assertThrows(NullPointerException.class,
                () -> Order.create(tenantId, null, fixedClock));
    }

    @Test
    void selfTransitionRaisesException() {
        Order order = Order.create(
                new TenantId(UUID.randomUUID()),
                CustomerSessionId.newId(),
                fixedClock);

        assertThrows(InvalidTransitionException.class,
                () -> order.transitionTo(OrderStatus.CREATED, fixedClock));
    }

    @Test
    void disallowedTransitionRaisesException() {
        Order order = Order.create(
                new TenantId(UUID.randomUUID()),
                CustomerSessionId.newId(),
                fixedClock);

        assertThrows(InvalidTransitionException.class,
                () -> order.transitionTo(OrderStatus.READY, fixedClock));
    }

    @Test
    void transitionFromTerminalRaisesException() {
        Order order = Order.create(
                new TenantId(UUID.randomUUID()),
                CustomerSessionId.newId(),
                fixedClock);

        advanceOrderToState(order, OrderStatus.COMPLETED);

        assertThrows(TerminalStateException.class,
                () -> order.transitionTo(OrderStatus.CANCELLED, fixedClock));
    }

    private void advanceOrderToState(Order order, OrderStatus target) {
        while (order.getStatus() != target) {
            OrderStatus current = order.getStatus();
            OrderStatus next = findNextAllowedTransition(current, target);
            if (next != null) {
                order.transitionTo(next, fixedClock);
            } else {
                fail("Cannot reach target state " + target + " from " + current);
            }
        }
    }

    private OrderStatus findNextAllowedTransition(OrderStatus current, OrderStatus target) {
        if (current == target) return null;

        for (OrderStatus status : OrderStatus.values()) {
            if (current.canTransitionTo(status)) {
                if (canReachVia(status, target)) {
                    return status;
                }
            }
        }
        return null;
    }

    private boolean canReachVia(OrderStatus source, OrderStatus target) {
        if (source == target) return true;
        if (source.isTerminal() && source != target) return false;

        for (OrderStatus next : OrderStatus.values()) {
            if (source.canTransitionTo(next) && canReachVia(next, target)) {
                return true;
            }
        }
        return false;
    }

    @Provide
    net.jqwik.api.Arbitrary<AllowedTransition> allowedTransition() {
        return net.jqwik.api.Arbitraries.of(
                new AllowedTransition(OrderStatus.CREATED, OrderStatus.IN_PROGRESS),
                new AllowedTransition(OrderStatus.CREATED, OrderStatus.CANCELLED),
                new AllowedTransition(OrderStatus.IN_PROGRESS, OrderStatus.READY),
                new AllowedTransition(OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED),
                new AllowedTransition(OrderStatus.READY, OrderStatus.COMPLETED),
                new AllowedTransition(OrderStatus.READY, OrderStatus.CANCELLED)
        );
    }

    record AllowedTransition(OrderStatus source, OrderStatus target) {
    }
}

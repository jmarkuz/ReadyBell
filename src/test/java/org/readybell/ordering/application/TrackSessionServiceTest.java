package org.readybell.ordering.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.ordering.domain.InMemoryOrderRepository;
import org.readybell.ordering.domain.Order;
import org.readybell.restaurant.domain.InMemoryRestaurantRepository;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.session.domain.InMemoryCustomerSessionRepository;
import org.readybell.session.domain.SessionStatus;
import org.readybell.shared.domain.TenantId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrackSessionServiceTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private InMemoryCustomerSessionRepository sessions;
    private InMemoryOrderRepository orders;
    private InMemoryRestaurantRepository restaurants;
    private TrackSessionService service;

    @BeforeEach
    void setUp() {
        sessions = new InMemoryCustomerSessionRepository();
        orders = new InMemoryOrderRepository();
        restaurants = new InMemoryRestaurantRepository();
        service = new TrackSessionService(sessions, orders, restaurants);
    }

    private Order orderAt(CustomerSession session, Instant createdAt) {
        var order = Order.create(TENANT, session.getId(), Clock.fixed(createdAt, ZoneOffset.UTC));
        order.clearPendingEvents();
        orders.save(order);
        return order;
    }

    @Test
    void returnsRestaurantNameActiveFlagAndOrdersOldestFirst() {
        restaurants.save(Restaurant.create(TENANT, "cafe", RestaurantConfig.of("Café Aurora")));
        var session = CustomerSession.start(TENANT, Clock.fixed(T0, ZoneOffset.UTC));
        sessions.save(session);
        var later = orderAt(session, T0.plus(Duration.ofMinutes(5)));
        var earlier = orderAt(session, T0);

        var tracking = service.track(TENANT, session.getId()).orElseThrow();

        assertThat(tracking.restaurantName()).isEqualTo("Café Aurora");
        assertThat(tracking.sessionActive()).isTrue();
        assertThat(tracking.orders()).extracting(Order::getId).containsExactly(earlier.getId(), later.getId());
    }

    @Test
    void endedSessionIsReportedInactive() {
        var ended = CustomerSession.reconstitute(new CustomerSessionId(UUID.randomUUID()), TENANT,
                SessionStatus.ENDED, T0);
        sessions.save(ended);

        assertThat(service.track(TENANT, ended.getId()).orElseThrow().sessionActive()).isFalse();
    }

    @Test
    void missingRestaurantGivesAnEmptyName() {
        var session = CustomerSession.start(TENANT, Clock.fixed(T0, ZoneOffset.UTC));
        sessions.save(session);

        assertThat(service.track(TENANT, session.getId()).orElseThrow().restaurantName()).isEmpty();
    }

    @Test
    void unknownSessionIsEmpty() {
        assertThat(service.track(TENANT, new CustomerSessionId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void anotherTenantsSessionIsEmpty() {
        var session = CustomerSession.start(TENANT, Clock.fixed(T0, ZoneOffset.UTC));
        sessions.save(session);

        assertThat(service.track(new TenantId(UUID.randomUUID()), session.getId())).isEmpty();
    }

    @Test
    void constructorRejectsNulls() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TrackSessionService(null, orders, restaurants))
                .isInstanceOf(NullPointerException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TrackSessionService(sessions, null, restaurants))
                .isInstanceOf(NullPointerException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TrackSessionService(sessions, orders, null))
                .isInstanceOf(NullPointerException.class);
    }
}

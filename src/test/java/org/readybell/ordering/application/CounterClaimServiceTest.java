package org.readybell.ordering.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.ordering.domain.InMemoryOrderRepository;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderDetails;
import org.readybell.ordering.domain.OrderStatus;
import org.readybell.restaurant.domain.InMemoryRestaurantRepository;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CounterClaimServiceTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration MAX_AGE = Duration.ofHours(4);

    private InMemoryRestaurantRepository restaurants;
    private InMemoryOrderRepository orders;
    private Clock now;
    private CounterClaimService service;

    @BeforeEach
    void setUp() {
        restaurants = new InMemoryRestaurantRepository();
        orders = new InMemoryOrderRepository();
        restaurants.save(Restaurant.create(TENANT, "cafe", RestaurantConfig.of("Café Aurora")));
        now = Clock.fixed(T0.plus(Duration.ofMinutes(10)), ZoneOffset.UTC);
        service = new CounterClaimService(restaurants, orders, Clock.fixed(T0.plus(Duration.ofMinutes(10)), ZoneOffset.UTC), MAX_AGE);
    }

    private Order order(TenantId tenant, int number, Instant createdAt) {
        var order = Order.create(tenant, new CustomerSessionId(UUID.randomUUID()), Clock.fixed(createdAt, ZoneOffset.UTC),
                OrderDetails.NONE, number);
        order.clearPendingEvents();
        orders.save(order);
        return order;
    }

    @Test
    void claimReturnsTheOrdersSessionAndMarksItClaimed() {
        var order = order(TENANT, 101, T0);

        var session = service.claim("cafe", 101);

        assertThat(session).contains(order.getSessionId());
        assertThat(orders.findByTenantAndNumber(TENANT, 101).orElseThrow().isClaimed()).isTrue();
    }

    @Test
    void anOrderCanBeClaimedOnlyOnce() {
        order(TENANT, 101, T0);

        assertThat(service.claim("cafe", 101)).isPresent();
        assertThat(service.claim("cafe", 101)).isEmpty();
    }

    @Test
    void everyFailureIsTheSameEmptyResult() {
        order(TENANT, 101, T0);
        var other = new TenantId(UUID.randomUUID());
        restaurants.save(Restaurant.create(other, "bar", RestaurantConfig.of("Bar")));
        order(other, 7, T0);

        assertThat(service.claim("nope", 101)).isEmpty();           // unknown restaurant
        assertThat(service.claim("", 101)).isEmpty();               // blank identifier
        assertThat(service.claim("cafe", 999)).isEmpty();           // unknown number
        assertThat(service.claim("cafe", 7)).isEmpty();             // a number that only exists at another restaurant
    }

    @Test
    void finishedOrdersCannotBeClaimed() {
        var completed = order(TENANT, 1, T0);
        completed.transitionTo(OrderStatus.IN_PROGRESS, now);
        completed.transitionTo(OrderStatus.READY, now);
        completed.transitionTo(OrderStatus.COMPLETED, now);
        orders.save(completed);
        var cancelled = order(TENANT, 2, T0);
        cancelled.transitionTo(OrderStatus.CANCELLED, now);
        orders.save(cancelled);
        order(TENANT, 3, T0).transitionTo(OrderStatus.IN_PROGRESS, now);

        assertThat(service.claim("cafe", 1)).isEmpty();
        assertThat(service.claim("cafe", 2)).isEmpty();
        assertThat(service.claim("cafe", 3)).isPresent();            // still being prepared: claimable
    }

    @Test
    void ordersOlderThanTheWindowCannotBeClaimed() {
        order(TENANT, 1, T0.plus(Duration.ofMinutes(10)).minus(MAX_AGE).minusSeconds(1));
        order(TENANT, 2, T0.plus(Duration.ofMinutes(10)).minus(MAX_AGE).plusSeconds(1));

        assertThat(service.claim("cafe", 1)).isEmpty();
        assertThat(service.claim("cafe", 2)).isPresent();
    }

    @Test
    void deactivatedRestaurantsServeNothing() {
        order(TENANT, 101, T0);
        var restaurant = restaurants.findByIdentifier("cafe").orElseThrow();
        restaurant.deactivate();
        restaurants.save(restaurant);

        assertThat(service.claim("cafe", 101)).isEmpty();
        assertThat(service.restaurantName("cafe")).isEmpty();
    }

    @Test
    void restaurantNameIsPublicForActiveRestaurants() {
        assertThat(service.restaurantName("cafe")).contains("Café Aurora");
        assertThat(service.restaurantName("nope")).isEmpty();
        assertThat(service.restaurantName(null)).isEmpty();
    }
}

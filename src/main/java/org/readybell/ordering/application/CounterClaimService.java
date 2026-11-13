package org.readybell.ordering.application;

import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.session.domain.CustomerSessionId;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Implements {@link CounterClaimUseCase}. An order is claimable while it is unclaimed, not finished (completed or
 * cancelled) and younger than {@code maxAge}: receipt numbers are small and sequential, so the window keeps old numbers
 * from being guessed long after the customer left.
 */
public class CounterClaimService implements CounterClaimUseCase {

    private final RestaurantRepository restaurants;
    private final OrderRepository orders;
    private final Clock clock;
    private final Duration maxAge;

    public CounterClaimService(RestaurantRepository restaurants, OrderRepository orders, Clock clock, Duration maxAge) {
        this.restaurants = Objects.requireNonNull(restaurants, "restaurants must not be null");
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge must not be null");
    }

    @Override
    public Optional<String> restaurantName(String restaurantIdentifier) {
        return activeRestaurant(restaurantIdentifier).map(r -> r.getConfig().name());
    }

    @Override
    public Optional<CustomerSessionId> claim(String restaurantIdentifier, int orderNumber) {
        return activeRestaurant(restaurantIdentifier)
                .flatMap(restaurant -> orders.findByTenantAndNumber(restaurant.getTenantId(), orderNumber))
                .filter(this::isClaimable)
                .map(order -> {
                    order.claim(clock);
                    orders.save(order);   // the version check makes a concurrent second claim fail here
                    return order.getSessionId();
                });
    }

    private Optional<Restaurant> activeRestaurant(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        return restaurants.findByIdentifier(identifier).filter(Restaurant::isActive);
    }

    private boolean isClaimable(Order order) {
        return !order.isClaimed()
                && !order.getStatus().isTerminal()
                && order.getCreatedAt().plus(maxAge).isAfter(clock.instant());
    }
}

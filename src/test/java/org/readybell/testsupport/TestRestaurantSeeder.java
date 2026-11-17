package org.readybell.testsupport;

import org.readybell.platform.SecurityProperties;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.shared.domain.TenantId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Signing in names the restaurant, so the tenants of the configured test users (admin, other) need a restaurant row
 * with an identifier. The names are the ones {@code DevRestaurantSeeder} gives (first tenant {@code dev-restaurant},
 * the next {@code dev-restaurant-2}): the test database is shared by contexts of both profiles, and whichever seeds
 * first must not change what a test signs in with.
 */
@Component
@Profile("test & !dev")
public class TestRestaurantSeeder implements ApplicationRunner {

    private final SecurityProperties security;
    private final RestaurantRepository restaurants;

    public TestRestaurantSeeder(SecurityProperties security, RestaurantRepository restaurants) {
        this.security = security;
        this.restaurants = restaurants;
    }

    /** The restaurant a configured test user signs in to ({@code admin} and {@code other} have one, the operator none). */
    public static String restaurantFor(String configuredUser) {
        return switch (configuredUser) {
            case "admin" -> "dev-restaurant";
            case "other" -> "dev-restaurant-2";
            default -> "";
        };
    }

    @Override
    public void run(ApplicationArguments args) {
        int n = 1;
        for (UUID tenant : security.getUsers().stream().map(SecurityProperties.User::getTenantId)
                .filter(java.util.Objects::nonNull).distinct().toList()) {
            if (restaurants.findByTenantId(new TenantId(tenant)).isEmpty()) {
                String identifier = n == 1 ? "dev-restaurant" : "dev-restaurant-" + n;
                restaurants.save(Restaurant.create(new TenantId(tenant), identifier, RestaurantConfig.of("Test " + identifier)));
            }
            n++;
        }
    }
}

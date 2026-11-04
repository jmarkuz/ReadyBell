package org.readybell.platform.devdata;

import org.readybell.platform.SecurityProperties;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.shared.domain.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Development only: the seed users from {@code application-dev.yml} belong to fixed tenants, but the
 * dev schema starts empty. Without a restaurant row for those tenants, the restaurant profile, the
 * name in Settings and session starts would all answer "restaurant not found". This creates one
 * restaurant per seed tenant on startup (the platform operator has no tenant and is skipped).
 */
@Component
@Profile("dev")
public class DevRestaurantSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevRestaurantSeeder.class);

    private final SecurityProperties security;
    private final RestaurantRepository restaurants;

    public DevRestaurantSeeder(SecurityProperties security, RestaurantRepository restaurants) {
        this.security = security;
        this.restaurants = restaurants;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<UUID> tenants = new LinkedHashSet<>();
        for (SecurityProperties.User user : security.getUsers()) {
            if (user.getTenantId() != null) {
                tenants.add(user.getTenantId());
            }
        }
        int n = 1;
        for (UUID tenant : tenants) {
            TenantId id = new TenantId(tenant);
            if (restaurants.findByTenantId(id).isEmpty()) {
                String identifier = n == 1 ? "dev-restaurant" : "dev-restaurant-" + n;
                restaurants.save(Restaurant.create(id, identifier, RestaurantConfig.of("Dev Restaurant" + (n == 1 ? "" : " " + n))));
                log.info("Seeded dev restaurant '{}' for tenant {}", identifier, tenant);
            }
            n++;
        }
    }
}

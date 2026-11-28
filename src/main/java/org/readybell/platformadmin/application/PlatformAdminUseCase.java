package org.readybell.platformadmin.application;

import org.readybell.restaurant.application.RestaurantNotFoundException;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.shared.domain.TenantId;

import java.util.List;

/**
 * Inbound port for Platform Admin tenant-management operations.
 *
 * <p>Platform Admins are the only principals authorised to perform these
 * operations. Access control will be enforced at the HTTP adapter
 * layer via Spring Security (task 20/21).
 */
public interface PlatformAdminUseCase {
    /**
     * Creates a new Restaurant tenant with a unique, platform-assigned
     * {@link TenantId}.
     *
     * @throws IllegalArgumentException              if {@code identifier} is null or blank
     * @throws DuplicateRestaurantIdentifierException if a restaurant with the same identifier already exists
     */
    Restaurant createRestaurant(String identifier, RestaurantConfig config);

    /**
     * Creates a restaurant together with its first {@code RESTAURANT_ADMIN} account, so that someone
     * can sign in and manage it. The caller should run this in one transaction so that a failure
     * leaves neither behind.
     */
    Restaurant createRestaurant(String identifier, RestaurantConfig config,
                                String adminUsername, String adminPasswordHash);

    /**
     * Updates the configuration of an existing Restaurant.
     *
     * @throws RestaurantNotFoundException if no restaurant exists with the given {@code tenantId}
     */
    Restaurant updateRestaurant(TenantId tenantId, RestaurantConfig config);

    /**
     * Deactivates an existing Restaurant, preventing new customer sessions
     * from starting.
     *
     * @throws RestaurantNotFoundException if no restaurant exists with the given {@code tenantId}
     */
    Restaurant deactivateRestaurant(TenantId tenantId);

    /** Every restaurant on the platform, ordered by identifier. */
    List<Restaurant> listRestaurants();
}

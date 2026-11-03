package org.readybell.platformadmin.application;

import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.shared.domain.TenantId;

/**
 * Inbound port for Platform Admin tenant-management operations.
 *
 * <p>Platform Admins are the only principals authorised to perform these
 * operations (Req 9.4). Access control will be enforced at the HTTP adapter
 * layer via Spring Security (task 20/21).
 */
public interface PlatformAdminUseCase {

    /**
     * Creates a new Restaurant tenant with a unique, platform-assigned
     * {@link TenantId} (Req 9.2).
     *
     * @param identifier a unique business identifier for the restaurant (Req 9.5)
     * @param config     initial restaurant configuration
     * @return the newly created, active {@link Restaurant}
     * @throws IllegalArgumentException              if {@code identifier} is null or blank (Req 9.5)
     * @throws DuplicateRestaurantIdentifierException if a restaurant with the same identifier already exists (Req 9.5)
     */
    Restaurant createRestaurant(String identifier, RestaurantConfig config);

    /**
     * Creates a restaurant together with its first {@code RESTAURANT_ADMIN} account, so that someone
     * can sign in and manage it. The caller should run this in one transaction so that a failure
     * leaves neither behind.
     *
     * @param adminUsername     username of the first admin (platform-wide unique)
     * @param adminPasswordHash encoded password of the first admin
     * @throws org.readybell.restaurant.application.DuplicateUsernameException if the username is taken
     */
    Restaurant createRestaurant(String identifier, RestaurantConfig config,
                                String adminUsername, String adminPasswordHash);

    /**
     * Updates the configuration of an existing Restaurant (Req 8.6).
     *
     * @param tenantId the unique tenant identifier
     * @param config   the replacement configuration
     * @return the updated {@link Restaurant}
     * @throws RestaurantNotFoundException if no restaurant exists with the given {@code tenantId}
     */
    Restaurant updateRestaurant(TenantId tenantId, RestaurantConfig config);

    /**
     * Deactivates an existing Restaurant, preventing new customer sessions
     * from starting (Req 9.3, 9.6).
     *
     * @param tenantId the unique tenant identifier
     * @return the deactivated {@link Restaurant}
     * @throws RestaurantNotFoundException if no restaurant exists with the given {@code tenantId}
     */
    Restaurant deactivateRestaurant(TenantId tenantId);
}

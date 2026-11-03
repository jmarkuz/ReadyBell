package org.readybell.platformadmin.adapter.in.web;

import org.readybell.restaurant.domain.Restaurant;

import java.util.UUID;

/**
 * Response body for restaurant CRUD operations (Req 9.2).
 *
 * @param tenantId   the platform-assigned unique tenant identifier
 * @param identifier the business identifier of the restaurant
 * @param status     the current restaurant status ({@code ACTIVE} or {@code DEACTIVATED})
 */
public record RestaurantResponse(UUID tenantId, String identifier, String status, String adminUsername) {

    /** Response for operations that do not create an admin account. */
    public RestaurantResponse(UUID tenantId, String identifier, String status) {
        this(tenantId, identifier, status, null);
    }

    /**
     * Builds a {@code RestaurantResponse} from a {@link Restaurant} aggregate.
     *
     * @param restaurant the restaurant aggregate to map
     * @return a response record populated from the aggregate's current state
     */
    public static RestaurantResponse withAdmin(Restaurant restaurant, String adminUsername) {
        return new RestaurantResponse(restaurant.getTenantId().value(), restaurant.getIdentifier(),
                restaurant.getStatus().name(), adminUsername);
    }

    public static RestaurantResponse from(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getTenantId().value(),
                restaurant.getIdentifier(),
                restaurant.getStatus().name()
        );
    }
}

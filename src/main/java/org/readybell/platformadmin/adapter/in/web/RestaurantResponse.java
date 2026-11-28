package org.readybell.platformadmin.adapter.in.web;

import org.readybell.restaurant.domain.Restaurant;

import java.util.UUID;

/** Response body for restaurant CRUD operations. */
public record RestaurantResponse(UUID tenantId, String identifier, String status, String adminUsername, String name) {
    public RestaurantResponse(UUID tenantId, String identifier, String status) {
        this(tenantId, identifier, status, null, null);
    }

    public RestaurantResponse(UUID tenantId, String identifier, String status, String adminUsername) {
        this(tenantId, identifier, status, adminUsername, null);
    }

    public static RestaurantResponse withAdmin(Restaurant restaurant, String adminUsername) {
        return new RestaurantResponse(restaurant.getTenantId().value(), restaurant.getIdentifier(),
                restaurant.getStatus().name(), adminUsername, restaurant.getConfig().name());
    }

    public static RestaurantResponse from(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getTenantId().value(),
                restaurant.getIdentifier(),
                restaurant.getStatus().name(),
                null,
                restaurant.getConfig().name()
        );
    }
}

package com.example.restaurant.application;

import com.example.shared.domain.TenantId;

/**
 * Thrown when a restaurant cannot be found for a given tenant (Req 8.8).
 *
 * <p>Surfaced when an admin attempts to update configuration for a restaurant that
 * does not exist or does not belong to their tenant.
 */
public class RestaurantNotFoundException extends RuntimeException {

    private final TenantId tenantId;

    public RestaurantNotFoundException(TenantId tenantId) {
        super("Restaurant not found for tenant " + tenantId.value());
        this.tenantId = tenantId;
    }

    public TenantId getTenantId() {
        return tenantId;
    }
}

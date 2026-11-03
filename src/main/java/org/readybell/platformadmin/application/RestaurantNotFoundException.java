package org.readybell.platformadmin.application;

import org.readybell.shared.domain.TenantId;

/**
 * Thrown when a Platform Admin operation targets a Restaurant that does not exist.
 */
public class RestaurantNotFoundException extends RuntimeException {

    private final TenantId tenantId;

    public RestaurantNotFoundException(TenantId tenantId) {
        super("No restaurant found with tenantId '" + tenantId.value() + "'");
        this.tenantId = tenantId;
    }

    /** The tenant identifier for which no restaurant was found. */
    public TenantId getTenantId() {
        return tenantId;
    }
}

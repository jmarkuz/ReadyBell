package com.example.restaurant.application;

import com.example.shared.domain.TenantId;

/**
 * Thrown when a Restaurant Admin attempts to create a staff account whose username
 * already exists within the same tenant (Req 8.4).
 *
 * <p>The operation is rejected and existing account data is left unchanged (Req 8.4).
 */
public class DuplicateUsernameException extends RuntimeException {

    private final TenantId tenantId;
    private final String username;

    public DuplicateUsernameException(TenantId tenantId, String username) {
        super("Username '" + username + "' already exists for tenant " + tenantId.value());
        this.tenantId = tenantId;
        this.username = username;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public String getUsername() {
        return username;
    }
}

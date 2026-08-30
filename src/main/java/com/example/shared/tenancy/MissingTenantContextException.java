package com.example.shared.tenancy;

/**
 * Signals that a tenant-scoped operation was attempted with no resolved tenant
 * in scope.
 *
 * <p>Raised by {@link TenantContextHolder#require()} when no {@code TenantId}
 * has been resolved for the current request. The message conveys that the
 * "tenant context missing" so callers can map it to a rejection with zero
 * records (Req 10.5).
 */
public class MissingTenantContextException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "tenant context missing";

    public MissingTenantContextException() {
        super(DEFAULT_MESSAGE);
    }

    public MissingTenantContextException(String message) {
        super(message);
    }
}

package org.readybell.shared.tenancy;

import java.util.Objects;
import java.util.Optional;

import org.readybell.shared.domain.TenantId;

/**
 * Holds the resolved {@link TenantId} for the current request scope.
 *
 * <p>A {@code TenantContext} is a small mutable container: it starts empty and is
 * populated by the inbound tenant-resolution mechanics once a request's tenant is
 * known. Application and persistence layers read the current tenant
 * from here to constrain every tenant-scoped data access.
 *
 * <p>This type is part of the framework-light shared kernel and carries no Spring
 * or persistence annotations. The request-scoped, thread-bound access is provided
 * by {@link TenantContextHolder}.
 */
public final class TenantContext {
    private TenantId tenantId;

    public TenantContext() {
        this.tenantId = null;
    }

    public TenantContext(TenantId tenantId) {
        this.tenantId = tenantId;
    }

    public void set(TenantId tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    }

    public Optional<TenantId> get() {
        return Optional.ofNullable(tenantId);
    }

    public boolean isResolved() {
        return tenantId != null;
    }

    /** Clears any resolved tenant, returning the context to the empty state. */
    public void clear() {
        this.tenantId = null;
    }
}

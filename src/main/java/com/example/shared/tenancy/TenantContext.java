package com.example.shared.tenancy;

import java.util.Objects;
import java.util.Optional;

import com.example.shared.domain.TenantId;

/**
 * Holds the resolved {@link TenantId} for the current request scope.
 *
 * <p>A {@code TenantContext} is a small mutable container: it starts empty and is
 * populated by the inbound tenant-resolution mechanics once a request's tenant is
 * known (Req 10.4). Application and persistence layers read the current tenant
 * from here to constrain every tenant-scoped data access.
 *
 * <p>This type is part of the framework-light shared kernel and carries no Spring
 * or persistence annotations. The request-scoped, thread-bound access is provided
 * by {@link TenantContextHolder}.
 */
public final class TenantContext {

    private TenantId tenantId;

    /** Creates an empty context with no resolved tenant. */
    public TenantContext() {
        this.tenantId = null;
    }

    /** Creates a context pre-resolved to the given tenant. */
    public TenantContext(TenantId tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Resolves this context to the given tenant (Req 10.4).
     *
     * @param tenantId the request's tenant; must not be {@code null}
     */
    public void set(TenantId tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    }

    /** Returns the resolved tenant, if any. */
    public Optional<TenantId> get() {
        return Optional.ofNullable(tenantId);
    }

    /** Returns {@code true} when a tenant has been resolved for this context. */
    public boolean isResolved() {
        return tenantId != null;
    }

    /** Clears any resolved tenant, returning the context to the empty state. */
    public void clear() {
        this.tenantId = null;
    }
}

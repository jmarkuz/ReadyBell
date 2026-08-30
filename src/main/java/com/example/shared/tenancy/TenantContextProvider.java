package com.example.shared.tenancy;

import com.example.shared.domain.TenantId;

/**
 * Port through which application services obtain the current request's tenant.
 *
 * <p>Application services depend on this interface rather than calling
 * {@link TenantContextHolder#require()} directly, keeping business logic
 * decoupled from the thread-bound infrastructure mechanism (Req 10.4, 10.5).
 *
 * <p>The production implementation delegates to {@link TenantContextHolder};
 * tests supply a fixed-value fake ({@code FixedTenantContextProvider}).
 */
public interface TenantContextProvider {

    /**
     * Returns the current request's resolved {@link TenantId}.
     *
     * @return the resolved tenant, never {@code null}
     * @throws MissingTenantContextException if no tenant has been resolved for
     *     the current request (Req 10.5)
     */
    TenantId requireTenant();
}

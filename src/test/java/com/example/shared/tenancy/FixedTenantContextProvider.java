package com.example.shared.tenancy;

import java.util.Objects;

import com.example.shared.domain.TenantId;

/**
 * Test fake that always returns the same {@link TenantId}.
 *
 * <p>Use this in unit tests instead of wiring the real thread-bound
 * {@link TenantContextHolder}. Keeps tests simple, deterministic, and free
 * from request-lifecycle concerns.
 *
 * <pre>{@code
 * TenantContextProvider ctx = FixedTenantContextProvider.of(someTenantId);
 * }</pre>
 */
public final class FixedTenantContextProvider implements TenantContextProvider {

    private final TenantId tenantId;

    /**
     * Creates a provider that always returns {@code tenantId}.
     *
     * @param tenantId the fixed tenant to return; must not be {@code null}
     */
    public FixedTenantContextProvider(TenantId tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    }

    /**
     * Convenience factory method.
     *
     * @param tenantId the fixed tenant to return; must not be {@code null}
     * @return a new {@code FixedTenantContextProvider} bound to {@code tenantId}
     */
    public static FixedTenantContextProvider of(TenantId tenantId) {
        return new FixedTenantContextProvider(tenantId);
    }

    /** Always returns the fixed {@link TenantId} supplied at construction time. */
    @Override
    public TenantId requireTenant() {
        return tenantId;
    }
}

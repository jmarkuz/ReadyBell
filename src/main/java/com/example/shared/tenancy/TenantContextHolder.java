package com.example.shared.tenancy;

import java.util.Optional;

import com.example.shared.domain.TenantId;

/**
 * Static, thread-bound access to the current request's {@link TenantContext}.
 *
 * <p>Backed by a {@link ThreadLocal}, this holder exposes the resolved tenant to
 * any code executing on the request thread without threading a {@code TenantId}
 * through every method signature. The inbound tenant-resolution mechanics call
 * {@link #set(TenantId)} at the start of a request and {@link #clear()} once the
 * request completes (Req 10.4).
 *
 * <p>Kept framework-light: plain Java only, no Spring or persistence annotations.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT =
            ThreadLocal.withInitial(TenantContext::new);

    private TenantContextHolder() {
    }

    /**
     * Resolves the current thread's context to the given tenant (Req 10.4).
     *
     * @param tenantId the request's tenant; must not be {@code null}
     */
    public static void set(TenantId tenantId) {
        CONTEXT.get().set(tenantId);
    }

    /** Returns the current thread's resolved tenant, if any. */
    public static Optional<TenantId> get() {
        return CONTEXT.get().get();
    }

    /** Returns {@code true} when a tenant is resolved for the current thread. */
    public static boolean isResolved() {
        return CONTEXT.get().isResolved();
    }

    /**
     * Returns the current thread's resolved {@link TenantId}.
     *
     * @return the resolved tenant, never {@code null}
     * @throws MissingTenantContextException if no tenant has been resolved for the
     *     current request, conveying that the tenant context is missing (Req 10.5)
     */
    public static TenantId require() {
        return get().orElseThrow(MissingTenantContextException::new);
    }

    /**
     * Clears the current thread's tenant context.
     *
     * <p>Must be invoked at the end of request processing to avoid leaking a
     * resolved tenant onto a pooled thread serving a later request.
     */
    public static void clear() {
        CONTEXT.remove();
    }
}

package org.readybell.shared.tenancy;

import org.readybell.shared.domain.TenantId;
import org.springframework.stereotype.Component;

/**
 * Production implementation of {@link TenantContextProvider} that delegates to the
 * thread-bound {@link TenantContextHolder}.
 *
 * <p>Application services inject this via the {@link TenantContextProvider} port; they
 * never depend on {@link TenantContextHolder} directly, keeping business logic decoupled
 * from the threading mechanism (; ADR-006).
 *
 * <p>Registered as a Spring {@code @Component} so it is auto-discovered and can be
 * injected wherever the port interface is required.
 */
@Component
public class TenantContextHolderProvider implements TenantContextProvider {
    @Override
    public TenantId requireTenant() {
        return TenantContextHolder.require();
    }
}

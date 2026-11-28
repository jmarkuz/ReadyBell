package org.readybell.ordering.application;

import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;

/**
 * Thrown by {@link ManageOrderUseCase#createOrder} when the target
 * {@link CustomerSession} cannot be found for the
 * given tenant, or is not in an active state.
 *
 * <p>Covers two cases that are deliberately indistinguishable to callers:
 * <ul>
 *   <li>No session with that {@link CustomerSessionId} exists.</li>
 *   <li>A session with that id exists but belongs to a different tenant — cross-tenant
 *       records are silently excluded.</li>
 *   <li>The session exists but is not active.</li>
 * </ul>
 */
public class SessionNotFoundException extends RuntimeException {
    private final TenantId tenantId;
    private final CustomerSessionId sessionId;

    public SessionNotFoundException(TenantId tenantId, CustomerSessionId sessionId) {
        super("Session not found or not active for tenant " + tenantId.value()
                + " and sessionId " + sessionId.value());
        this.tenantId  = tenantId;
        this.sessionId = sessionId;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public CustomerSessionId getSessionId() {
        return sessionId;
    }
}

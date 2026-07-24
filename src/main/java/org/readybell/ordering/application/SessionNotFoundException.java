package org.readybell.ordering.application;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;

/**
 * Thrown by {@link ManageOrderUseCase#createOrder} when the target
 * {@link org.readybell.session.domain.CustomerSession} cannot be found for the
 * given tenant, or is not in an active state.
 *
 * <p>Covers two cases that are deliberately indistinguishable to callers:
 * <ul>
 *   <li>No session with that {@link CustomerSessionId} exists.</li>
 *   <li>A session with that id exists but belongs to a different tenant — cross-tenant
 *       records are silently excluded (Req 10.3).</li>
 *   <li>The session exists but is not active (Req 4.5).</li>
 * </ul>
 *
 * <p>Requirements: 4.5, 4.6, 10.3.
 */
public class SessionNotFoundException extends RuntimeException {

    private final TenantId tenantId;
    private final CustomerSessionId sessionId;

    /**
     * @param tenantId  the tenant scope in which the lookup was performed
     * @param sessionId the session identity that was not found or was inactive
     */
    public SessionNotFoundException(TenantId tenantId, CustomerSessionId sessionId) {
        super("Session not found or not active for tenant " + tenantId.value()
                + " and sessionId " + sessionId.value());
        this.tenantId  = tenantId;
        this.sessionId = sessionId;
    }

    /** The tenant scope used during the lookup. */
    public TenantId getTenantId() {
        return tenantId;
    }

    /** The session identity that was not found. */
    public CustomerSessionId getSessionId() {
        return sessionId;
    }
}

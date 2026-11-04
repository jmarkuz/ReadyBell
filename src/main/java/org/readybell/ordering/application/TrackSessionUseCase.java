package org.readybell.ordering.application;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;

import java.util.Optional;

/** Read side of the customer tracking page (US-C002): one session with its orders. */
public interface TrackSessionUseCase {

    /**
     * @return the session's tracking view, or empty when the session does not exist in the tenant
     */
    Optional<SessionTracking> track(TenantId tenantId, CustomerSessionId sessionId);
}

package org.readybell.ordering.application;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;

import java.util.Optional;

/** Read side of the customer tracking page (US-C002): one session with its orders. */
public interface TrackSessionUseCase {
    Optional<SessionTracking> track(TenantId tenantId, CustomerSessionId sessionId);
}

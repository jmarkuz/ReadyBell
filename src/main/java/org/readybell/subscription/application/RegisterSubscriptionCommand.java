package org.readybell.subscription.application;

import org.readybell.subscription.domain.PushEndpoint;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;

import java.util.Objects;

/**
 * Command carrying all data required to register (or update) a Web Push subscription
 * for a specific customer session and tenant.
 *
 * <p>All fields are mandatory; the {@link PushEndpoint}
 * value object validates endpoint + key completeness at construction time.
 */
public record RegisterSubscriptionCommand(
        CustomerSessionId sessionId,
        TenantId tenantId,
        String endpointUrl,
        String p256dhKey,
        String authKey) {
    public RegisterSubscriptionCommand {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        // endpointUrl, p256dhKey, authKey are validated by PushEndpoint inside the service
    }
}

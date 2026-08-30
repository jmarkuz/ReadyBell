package com.example.subscription.application;

import com.example.subscription.domain.PushEndpoint;
import com.example.session.domain.CustomerSessionId;
import com.example.shared.domain.TenantId;

import java.util.Objects;

/**
 * Command carrying all data required to register (or update) a Web Push subscription
 * for a specific customer session and tenant.
 *
 * <p>All fields are mandatory; the {@link PushEndpoint}
 * value object validates endpoint + key completeness at construction time (Req 3.3).
 *
 * <p>Requirements covered: 3.1, 3.2, 3.3.
 *
 * @param sessionId   the customer session to bind the subscription to
 * @param tenantId    the tenant that owns this subscription
 * @param endpointUrl the push service URL for this device
 * @param p256dhKey   the client's P-256 Diffie-Hellman public key, base64url-encoded
 * @param authKey     the client's authentication secret, base64url-encoded
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

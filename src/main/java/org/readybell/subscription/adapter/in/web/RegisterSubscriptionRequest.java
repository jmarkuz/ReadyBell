package org.readybell.subscription.adapter.in.web;

import org.readybell.subscription.domain.PushEndpoint;

/**
 * Request body DTO for {@code POST /api/sessions/{sessionId}/subscriptions}.
 *
 * <p>Carries the three fields that identify a Web Push endpoint and its encryption
 * keys. Validation of completeness happens inside the domain's
 * {@link PushEndpoint} constructor so the
 * controller can pass the raw strings through (Req 3.2, 3.3).
 *
 * @param endpointUrl the push service URL for this device
 * @param p256dhKey   the client's P-256 Diffie-Hellman public key, base64url-encoded
 * @param authKey     the client's authentication secret, base64url-encoded
 */
public record RegisterSubscriptionRequest(
        String endpointUrl,
        String p256dhKey,
        String authKey) {
}

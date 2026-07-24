package org.readybell.subscription.adapter.in.web;

import org.readybell.subscription.domain.DeviceSubscription;

import java.util.UUID;

/**
 * Response body DTO returned from {@code POST /api/sessions/{sessionId}/subscriptions}.
 *
 * <p>Contains the identity of the persisted (or updated) subscription so the client
 * can use it later to revoke the subscription via the DELETE endpoint (Req 3.5).
 *
 * @param subscriptionId the UUID of the stored {@link DeviceSubscription}
 * @param sessionId      the UUID of the customer session this subscription belongs to
 */
public record SubscriptionResponse(UUID subscriptionId, UUID sessionId) {

    /**
     * Creates a {@code SubscriptionResponse} from a {@link DeviceSubscription} aggregate.
     *
     * @param subscription the persisted (new or updated) device subscription
     * @return a response DTO carrying the subscription and session identities
     */
    public static SubscriptionResponse from(DeviceSubscription subscription) {
        return new SubscriptionResponse(
                subscription.getId().value(),
                subscription.getSessionId().value());
    }
}

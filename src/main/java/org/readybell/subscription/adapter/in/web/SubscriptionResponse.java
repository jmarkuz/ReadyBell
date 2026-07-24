package org.readybell.subscription.adapter.in.web;

/**
 * Response body DTO returned from {@code POST /api/sessions/{sessionId}/subscriptions}.
 *
 * <p>Contains the identity of the persisted (or updated) subscription so the client
 * can use it later to revoke the subscription via the DELETE endpoint (Req 3.5).
 *
 * @param subscriptionId the UUID string value of the stored {@link
 *                       org.readybell.subscription.domain.DeviceSubscriptionId}
 */
public record SubscriptionResponse(String subscriptionId) {
}

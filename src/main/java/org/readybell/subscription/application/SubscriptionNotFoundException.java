package org.readybell.subscription.application;

import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.DeviceSubscriptionId;

/**
 * Thrown when a {@link DeviceSubscription} cannot be
 * found by its identity, or when the subscription exists but belongs to a different tenant
 * than the one making the request.
 *
 * <p>Covers the "no subscription found" path in {@code deleteSubscription} (Req 3.5) and
 * the tenant-isolation guard that prevents a caller from deleting another tenant's
 * subscriptions (Req 10.3).
 */
public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(DeviceSubscriptionId id) {
        super("DeviceSubscription not found: " + id.value());
    }

    public SubscriptionNotFoundException(String message) {
        super(message);
    }
}

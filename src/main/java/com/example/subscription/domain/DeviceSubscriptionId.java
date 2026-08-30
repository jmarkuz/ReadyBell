package com.example.subscription.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity value object for a {@link DeviceSubscription}.
 *
 * <p>Wraps a non-null {@link UUID} that uniquely identifies one registered
 * device push subscription (Req 3.1).
 */
public record DeviceSubscriptionId(UUID value) {

    public DeviceSubscriptionId {
        Objects.requireNonNull(value, "deviceSubscriptionId must not be null");
    }

    /** Creates a new, random {@code DeviceSubscriptionId}. */
    public static DeviceSubscriptionId newId() {
        return new DeviceSubscriptionId(UUID.randomUUID());
    }
}

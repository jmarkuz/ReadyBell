package org.readybell.notification.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity value object for a {@link Notification}.
 *
 * <p>Wraps a non-null {@link UUID} that uniquely identifies one outbound push notification.
 */
public record NotificationId(UUID value) {

    public NotificationId {
        Objects.requireNonNull(value, "notificationId must not be null");
    }

    /** Creates a new, random {@code NotificationId}. */
    public static NotificationId newId() {
        return new NotificationId(UUID.randomUUID());
    }
}

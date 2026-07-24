package org.readybell.notification.domain;

import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a {@link Notification} transitions to
 * {@link NotificationStatus#DELIVERED}.
 *
 * <p>Published through the {@code DomainEventPublisher} port after the push service confirms
 * that the message was delivered to the device (Req 6.5).
 *
 * @param eventId        unique identity of this event occurrence
 * @param tenantId       the tenant this event belongs to
 * @param notificationId the notification that was delivered
 * @param occurredAt     the instant at which delivery was confirmed
 */
public record NotificationDelivered(
        UUID eventId,
        TenantId tenantId,
        NotificationId notificationId,
        Instant occurredAt
) implements DomainEvent {
}

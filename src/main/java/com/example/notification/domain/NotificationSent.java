package com.example.notification.domain;

import com.example.shared.domain.DomainEvent;
import com.example.shared.domain.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a {@link Notification} transitions to {@link NotificationStatus#SENT}.
 *
 * <p>Published through the {@code DomainEventPublisher} port after the push service has accepted
 * the notification for delivery (Req 6.4).
 *
 * @param eventId        unique identity of this event occurrence
 * @param tenantId       the tenant this event belongs to
 * @param notificationId the notification that was sent
 * @param occurredAt     the instant at which the send was accepted
 */
public record NotificationSent(
        UUID eventId,
        TenantId tenantId,
        NotificationId notificationId,
        Instant occurredAt
) implements DomainEvent {
}

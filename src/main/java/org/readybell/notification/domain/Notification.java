package org.readybell.notification.domain;

import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.domain.DeviceSubscriptionId;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate representing a single push notification composed for one device subscription.
 *
 * <p>Lifecycle transitions (Req 6.4, 6.5):
 * <pre>
 *   PENDING ──▶ SENT ──▶ DELIVERED
 *      └──▶ FAILED
 *           SENT ──▶ FAILED
 * </pre>
 *
 * <p>State changes are recorded as domain events via {@link #pendingEvents()} so that the
 * application layer can publish them through the {@code DomainEventPublisher} port after the
 * transaction commits.
 */
public class Notification {

    private final NotificationId id;
    private final TenantId tenantId;
    private final DeviceSubscriptionId subscriptionId;
    private NotificationStatus status;
    private final String payload;
    /** The order that triggered this notification; lets a replayed event be recognised (may be null). */
    private final UUID orderId;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    private Notification(NotificationId id,
                         TenantId tenantId,
                         DeviceSubscriptionId subscriptionId,
                         NotificationStatus status,
                         String payload,
                         UUID orderId) {
        this.orderId = orderId;
        this.id = Objects.requireNonNull(id, "notificationId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
    }

    /**
     * Factory method — creates a new {@code Notification} in {@link NotificationStatus#PENDING}.
     *
     * @param tenantId       the tenant that owns the notification
     * @param subscriptionId the target device subscription
     * @param payload        the push message body
     * @return a new {@code Notification} in PENDING status with no pending events
     */
    public static Notification compose(TenantId tenantId,
                                       DeviceSubscriptionId subscriptionId,
                                       String payload) {
        return compose(tenantId, subscriptionId, payload, null);
    }

    /** Composes a notification caused by the given order. */
    public static Notification compose(TenantId tenantId,
                                       DeviceSubscriptionId subscriptionId,
                                       String payload,
                                       UUID orderId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(subscriptionId, "subscriptionId must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        return new Notification(NotificationId.newId(), tenantId, subscriptionId,
                NotificationStatus.PENDING, payload, orderId);
    }

    /**
     * Transitions from {@link NotificationStatus#PENDING} to {@link NotificationStatus#SENT}
     * and records a {@link NotificationSent} domain event (Req 6.4).
     *
     * @param clock used to timestamp the event
     * @throws IllegalStateException if the notification is not in PENDING status
     */
    public void markSent(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot mark SENT: current status is " + status + " (expected PENDING)");
        }
        status = NotificationStatus.SENT;
        pendingEvents.add(new NotificationSent(UUID.randomUUID(), tenantId, id, clock.instant()));
    }

    /**
     * Transitions from {@link NotificationStatus#SENT} to {@link NotificationStatus#DELIVERED}
     * and records a {@link NotificationDelivered} domain event (Req 6.5).
     *
     * @param clock used to timestamp the event
     * @throws IllegalStateException if the notification is not in SENT status
     */
    public void markDelivered(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (status != NotificationStatus.SENT) {
            throw new IllegalStateException(
                    "Cannot mark DELIVERED: current status is " + status + " (expected SENT)");
        }
        status = NotificationStatus.DELIVERED;
        pendingEvents.add(new NotificationDelivered(UUID.randomUUID(), tenantId, id, clock.instant()));
    }

    /**
     * Transitions to {@link NotificationStatus#FAILED} from any non-terminal state.
     * Records no domain event — failures are observed by the absence of a success event
     * and by the persisted FAILED status.
     *
     * @throws IllegalStateException if the notification is already DELIVERED
     */
    public void markFailed() {
        if (status == NotificationStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Cannot mark FAILED: notification is already DELIVERED");
        }
        status = NotificationStatus.FAILED;
    }

    /**
     * Returns the domain events recorded since this aggregate was created or loaded.
     * The returned list is unmodifiable; events are cleared by the application layer after
     * publishing.
     */
    public List<DomainEvent> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    public NotificationId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public DeviceSubscriptionId subscriptionId() {
        return subscriptionId;
    }

    public NotificationStatus status() {
        return status;
    }

    public String payload() {
        return payload;
    }

    /**
     * Rebuilds a {@code Notification} from its stored state.
     *
     * <p>Intended for use by the JPA persistence adapter only; no domain events are
     * recorded.
     *
     * @param id             the persisted notification identity
     * @param tenantId       the persisted tenant
     * @param subscriptionId the persisted subscription
     * @param status         the persisted notification status
     * @param payload        the persisted payload
     * @return a notification aggregate with no pending events
     */
    public static Notification reconstitute(NotificationId id,
                                            TenantId tenantId,
                                            DeviceSubscriptionId subscriptionId,
                                            NotificationStatus status,
                                            String payload) {
        return new Notification(id, tenantId, subscriptionId, status, payload, null);
    }

    public static Notification reconstitute(NotificationId id,
                                            TenantId tenantId,
                                            DeviceSubscriptionId subscriptionId,
                                            NotificationStatus status,
                                            String payload,
                                            UUID orderId) {
        return new Notification(id, tenantId, subscriptionId, status, payload, orderId);
    }

    /** The order that caused this notification, or null when unknown. */
    public UUID orderId() {
        return orderId;
    }
}

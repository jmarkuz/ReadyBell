package org.readybell.notification.domain;

import org.readybell.shared.domain.DomainEvent;
import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.domain.DeviceSubscriptionId;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate representing a single push notification composed for one device subscription.
 *
 * <p>Lifecycle transitions:
 * <pre>
 *   PENDING ──▶ SENT ──▶ DELIVERED
 *      └──▶ FAILED
 *           SENT ──▶ FAILED
 * </pre>
 *
 * <p>A PENDING notification is a delivery task: it is due at {@link #nextAttemptAt()}, each attempt that fails
 * transiently moves that time forward ({@link #scheduleRetry}), and it is given up on after {@link #expiresAt()}.
 * Nothing waits in a thread between attempts.
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
    private final UUID orderId;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    /** Title and click target of the push; the body is {@link #payload}. Null for notifications stored before retries existed. */
    private String title;
    private String url;
    private Instant readyAt;
    private int attempts;
    /** When the next attempt is due; null once the notification is no longer waiting for one. */
    private Instant nextAttemptAt;
    private Instant expiresAt;
    private String lastError;
    private String kind = KIND_READY;

    public static final String KIND_READY = "READY";
    public static final String KIND_CANCELLED = "CANCELLED";

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

    /** Factory method — creates a new {@code Notification} in {@link NotificationStatus#PENDING}. */
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
     * Composes a delivery task for a push that is due at once: it is attempted as soon as a worker picks it up, retried
     * with backoff if the push service is unavailable, and abandoned after {@code expiresAt}.
     */
    public static Notification composeForDelivery(TenantId tenantId, DeviceSubscriptionId subscriptionId,
                                                  NotificationPayload message, UUID orderId,
                                                  Instant readyAt, Instant now, Instant expiresAt) {
        return composeForDelivery(tenantId, subscriptionId, message, orderId, readyAt, now, expiresAt, KIND_READY);
    }

    /** As above, for a push about something other than the order being ready. */
    public static Notification composeForDelivery(TenantId tenantId, DeviceSubscriptionId subscriptionId,
                                                  NotificationPayload message, UUID orderId,
                                                  Instant readyAt, Instant now, Instant expiresAt, String kind) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Notification n = compose(tenantId, subscriptionId, message.body(), orderId);
        n.title = message.title();
        n.url = message.url();
        n.readyAt = readyAt;
        n.nextAttemptAt = now;
        n.expiresAt = expiresAt;
        n.kind = Objects.requireNonNull(kind, "kind must not be null");
        return n;
    }

    /** The push to send: the stored title and click target with the stored body. */
    public NotificationPayload toMessage() {
        return new NotificationPayload(title == null || title.isBlank() ? "ReadyBell" : title, payload, url);
    }

    public boolean isDue(Instant now) {
        return status == NotificationStatus.PENDING && nextAttemptAt != null && !nextAttemptAt.isAfter(now);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    /** The attempt just made failed transiently: stay PENDING and try again at {@code at}. */
    public void scheduleRetry(Instant at, String error) {
        requirePending("schedule a retry");
        attempts++;
        nextAttemptAt = Objects.requireNonNull(at, "at must not be null");
        lastError = error;
    }

    /**
     * Transitions from {@link NotificationStatus#PENDING} to {@link NotificationStatus#SENT}
     * and records a {@link NotificationSent} domain event.
     *
     * @throws IllegalStateException if the notification is not in PENDING status
     */
    public void markSent(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot mark SENT: current status is " + status + " (expected PENDING)");
        }
        status = NotificationStatus.SENT;
        attempts++;
        nextAttemptAt = null;
        lastError = null;
        pendingEvents.add(new NotificationSent(UUID.randomUUID(), tenantId, id, clock.instant()));
    }

    /**
     * Transitions from {@link NotificationStatus#SENT} to {@link NotificationStatus#DELIVERED}
     * and records a {@link NotificationDelivered} domain event.
     *
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
        nextAttemptAt = null;
    }

    /** As {@link #markFailed()}, recording why (for the operator; never shown to the customer). */
    public void markFailed(String reason) {
        markFailed();
        lastError = reason;
    }

    private void requirePending(String what) {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException("Cannot " + what + ": current status is " + status + " (expected PENDING)");
        }
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

    public String title() { return title; }

    public String url() { return url; }

    public Instant readyAt() { return readyAt; }

    public int attempts() { return attempts; }

    public Instant nextAttemptAt() { return nextAttemptAt; }

    public Instant expiresAt() { return expiresAt; }

    public String lastError() { return lastError; }

    public String kind() { return kind; }

    /** Rebuilds a notification including its delivery state (persistence adapter only). */
    public static Notification reconstitute(NotificationId id, TenantId tenantId, DeviceSubscriptionId subscriptionId,
                                            NotificationStatus status, String payload, UUID orderId,
                                            String title, String url, Instant readyAt, int attempts,
                                            Instant nextAttemptAt, Instant expiresAt, String lastError, String kind) {
        Notification n = new Notification(id, tenantId, subscriptionId, status, payload, orderId);
        n.kind = kind == null ? KIND_READY : kind;
        n.title = title;
        n.url = url;
        n.readyAt = readyAt;
        n.attempts = attempts;
        n.nextAttemptAt = nextAttemptAt;
        n.expiresAt = expiresAt;
        n.lastError = lastError;
        return n;
    }
}

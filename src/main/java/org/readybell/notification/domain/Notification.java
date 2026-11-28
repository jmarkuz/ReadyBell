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
 * One push notification for one device subscription: PENDING, then SENT (accepted by the push service) and DELIVERED, or FAILED.
 *
 * <p>A PENDING notification is a delivery task. It is due at {@link #nextAttemptAt()}, each transient failure moves that time
 * forward ({@link #scheduleRetry}), and it is given up on after {@link #expiresAt()}. Nothing waits in a thread between attempts.
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

    public static Notification compose(TenantId tenantId,
                                       DeviceSubscriptionId subscriptionId,
                                       String payload) {
        return compose(tenantId, subscriptionId, payload, null);
    }

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

    public void markDelivered(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (status != NotificationStatus.SENT) {
            throw new IllegalStateException(
                    "Cannot mark DELIVERED: current status is " + status + " (expected SENT)");
        }
        status = NotificationStatus.DELIVERED;
        pendingEvents.add(new NotificationDelivered(UUID.randomUUID(), tenantId, id, clock.instant()));
    }

    /** Failures record no event: they are seen as the absence of a success event and the persisted FAILED status. */
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

package org.readybell.notification.domain;

import org.readybell.shared.domain.TenantId;

import org.readybell.subscription.domain.DeviceSubscriptionId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link Notification} aggregates.
 *
 * <p>This is an outbound port defined in the domain layer (Req 11.1, 11.2). Production
 * adapters (JPA, tenant-filtered) implement it in the adapter layer; in-memory fakes
 * implement it in tests.
 *
 * <p>Notifications are append-mostly: they are composed and persisted in
 * {@link NotificationStatus#PENDING}, then updated to {@link NotificationStatus#SENT},
 * {@link NotificationStatus#DELIVERED}, or {@link NotificationStatus#FAILED} as the
 * delivery flow progresses (Req 6.4, 6.5, 6.7, 6.8).
 */
public interface NotificationRepository {

    /**
     * Persists a new or updated {@link Notification}.
     *
     * @param notification the notification to save (must not be null)
     */
    void save(Notification notification);

    /**
     * Finds one of the tenant's notifications by its identity.
     *
     * @param tenantId the tenant scope (must not be null)
     * @param id the notification identity (must not be null)
     * @return the notification, or {@link Optional#empty()} if not found
     */
    /**
     * Whether a push for this order was already accepted by the push service for this subscription
     * (status SENT or DELIVERED). Used to make a replayed {@code OrderReady} event harmless.
     */
    boolean existsSent(TenantId tenantId, UUID orderId, DeviceSubscriptionId subscriptionId);

    /**
     * Whether a push for this order is already waiting for delivery or was accepted for this subscription (PENDING, SENT or
     * DELIVERED). A replayed {@code OrderReady} event must not queue a second push.
     */
    boolean existsLive(TenantId tenantId, UUID orderId, DeviceSubscriptionId subscriptionId);

    /**
     * Hands the due PENDING notifications (oldest first, at most {@code limit}) to one worker: each is leased for
     * {@code lease}, so concurrent workers, on this node or another, never get the same one. A worker that dies lets the
     * lease expire and the notification is handed out again. Spans every tenant: the worker serves them all.
     */
    List<Notification> claimDue(Instant now, Duration lease, int limit);

    /** PENDING notifications, due or not (a gauge for the operator). */
    long countPending();

    Optional<Notification> findByTenantAndId(TenantId tenantId, NotificationId id);
}

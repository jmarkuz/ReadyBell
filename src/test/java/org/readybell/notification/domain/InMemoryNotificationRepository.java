package org.readybell.notification.domain;

import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.domain.DeviceSubscriptionId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fake implementation of {@link NotificationRepository}.
 *
 * <p>Backed by a simple {@link HashMap}; intended for use in unit and property-based
 * tests that exercise application logic without any I/O. No Spring or JPA annotations.
 *
 * <p>Notifications are append-mostly: they are saved in
 * {@link NotificationStatus#PENDING} and then re-saved as their status progresses to
 * SENT, DELIVERED, or FAILED. The fake simply overwrites the stored entry on each
 * {@link #save}, matching the upsert semantics of the production JPA adapter.
 */
public class InMemoryNotificationRepository implements NotificationRepository {

    private final Map<NotificationId, Notification> store = new HashMap<>();

    @Override
    public void save(Notification notification) {
        store.put(notification.id(), notification);
    }

    @Override
    public boolean existsSent(TenantId tenantId, java.util.UUID orderId, DeviceSubscriptionId subscriptionId) {
        return store.values().stream().anyMatch(n -> n.tenantId().equals(tenantId)
                && orderId.equals(n.orderId()) && n.subscriptionId().equals(subscriptionId)
                && (n.status() == NotificationStatus.SENT || n.status() == NotificationStatus.DELIVERED));
    }

    @Override
    public boolean existsLive(TenantId tenantId, java.util.UUID orderId, DeviceSubscriptionId subscriptionId, String kind) {
        return store.values().stream().anyMatch(n -> n.tenantId().equals(tenantId)
                && orderId.equals(n.orderId()) && n.subscriptionId().equals(subscriptionId) && n.kind().equals(kind)
                && n.status() != NotificationStatus.FAILED);
    }

    /** Hands out the due PENDING notifications; the fake needs no lease because tests call it from one thread. */
    @Override
    public java.util.List<Notification> claimDue(java.time.Instant now, java.time.Duration lease, int limit) {
        return store.values().stream().filter(n -> n.isDue(now))
                .sorted(java.util.Comparator.comparing(Notification::nextAttemptAt))
                .limit(limit).toList();
    }

    @Override
    public long countPending() {
        return store.values().stream().filter(n -> n.status() == NotificationStatus.PENDING).count();
    }

    /** All stored notifications, for assertions. */
    public java.util.List<Notification> all() {
        return java.util.List.copyOf(store.values());
    }

    @Override
    public Optional<Notification> findByTenantAndId(TenantId tenantId, NotificationId id) {
        return Optional.ofNullable(store.get(id)).filter(n -> n.tenantId().equals(tenantId));
    }

    /** Convenience helper — returns the total number of stored notifications. */
    public int size() {
        return store.size();
    }

    /** Clears all stored notifications. Useful for test set-up/tear-down. */
    public void clear() {
        store.clear();
    }
}

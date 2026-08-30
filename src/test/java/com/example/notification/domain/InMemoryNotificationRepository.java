package com.example.notification.domain;

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
    public Optional<Notification> findById(NotificationId id) {
        return Optional.ofNullable(store.get(id));
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

package com.example.notification.domain;

import java.util.Optional;

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
     * Finds a notification by its identity.
     *
     * @param id the notification identity (must not be null)
     * @return the notification, or {@link Optional#empty()} if not found
     */
    Optional<Notification> findById(NotificationId id);
}

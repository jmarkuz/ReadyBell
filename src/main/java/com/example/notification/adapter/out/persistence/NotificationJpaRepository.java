package com.example.notification.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link NotificationJpaEntity}.
 *
 * <p>The Hibernate {@code tenantFilter} is enabled by the adapter
 * ({@link JpaNotificationRepository}) before any query, so standard
 * find-by-id methods automatically return only rows matching the current tenant
 * (Req 10.2, 10.4).
 */
public interface NotificationJpaRepository
        extends JpaRepository<NotificationJpaEntity, UUID> {
}

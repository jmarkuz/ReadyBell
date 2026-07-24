package org.readybell.notification.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.readybell.notification.domain.Notification;
import org.readybell.notification.domain.NotificationId;
import org.readybell.notification.domain.NotificationRepository;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA adapter implementing the {@link NotificationRepository} port.
 *
 * <h3>Tenant filter activation (Req 10.2, 10.4)</h3>
 * <p>The Hibernate {@code tenantFilter} is enabled using the resolved tenant from
 * {@link TenantContextHolder} before every {@code SELECT}, constraining results to
 * the current tenant and excluding cross-tenant rows (Req 10.3).
 */
@Repository
@Transactional
public class JpaNotificationRepository implements NotificationRepository {

    private final NotificationJpaRepository springRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaNotificationRepository(NotificationJpaRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void save(Notification notification) {
        var entity = NotificationJpaEntity.from(notification);
        springRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findById(NotificationId id) {
        enableTenantFilter();
        return springRepo.findById(id.value())
                .map(NotificationJpaEntity::toDomain);
    }

    // -------------------------------------------------------------------------
    // Tenant filter helper
    // -------------------------------------------------------------------------

    /**
     * Enables the Hibernate {@code tenantFilter} using the resolved tenant from
     * {@link TenantContextHolder} (Req 10.2, 10.4).
     */
    private void enableTenantFilter() {
        TenantId tenantId = TenantContextHolder.require();
        entityManager.unwrap(Session.class)
                .enableFilter("tenantFilter")
                .setParameter("tenantId", tenantId.value());
    }
}

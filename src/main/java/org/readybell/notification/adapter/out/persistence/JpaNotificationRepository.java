package org.readybell.notification.adapter.out.persistence;

import org.readybell.shared.domain.jpa.CrossTenant;
import org.readybell.notification.domain.Notification;
import org.readybell.notification.domain.NotificationId;
import org.readybell.notification.domain.NotificationRepository;
import org.readybell.shared.domain.TenantId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter implementing the {@link NotificationRepository} port.
 *
 * <h3>Tenant filter activation</h3>
 * <p>The Hibernate {@code tenantFilter} is enabled using the resolved tenant from
 * {@link TenantContextHolder} before every {@code SELECT}, constraining results to
 * the current tenant and excluding cross-tenant rows.
 */
@Repository
@Transactional
public class JpaNotificationRepository implements NotificationRepository {
    private final NotificationJpaRepository springRepo;

    @PersistenceContext
    private EntityManager em;

    public JpaNotificationRepository(NotificationJpaRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    @CrossTenant("the notification carries its own tenant")
    public void save(Notification notification) {
        var entity = NotificationJpaEntity.from(notification);
        springRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsSent(TenantId tenantId, java.util.UUID orderId,
                              org.readybell.subscription.domain.DeviceSubscriptionId subscriptionId) {
        return springRepo.existsByTenantIdAndOrderIdAndSubscriptionIdAndStatusIn(
                tenantId.value(), orderId, subscriptionId.value(),
                java.util.List.of(org.readybell.notification.domain.NotificationStatus.SENT,
                        org.readybell.notification.domain.NotificationStatus.DELIVERED));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsLive(TenantId tenantId, java.util.UUID orderId,
                              org.readybell.subscription.domain.DeviceSubscriptionId subscriptionId, String kind) {
        return springRepo.existsByTenantIdAndOrderIdAndSubscriptionIdAndKindAndStatusIn(
                tenantId.value(), orderId, subscriptionId.value(), kind,
                java.util.List.of(org.readybell.notification.domain.NotificationStatus.PENDING,
                        org.readybell.notification.domain.NotificationStatus.SENT,
                        org.readybell.notification.domain.NotificationStatus.DELIVERED));
    }

    @Override
    @CrossTenant("the delivery worker serves every tenant")
    @SuppressWarnings("unchecked")
    public List<Notification> claimDue(Instant now, Duration lease, int limit) {
        List<UUID> ids = ((List<Object>) em.createNativeQuery("""
                SELECT id FROM notification
                WHERE status = 'PENDING' AND next_attempt_at IS NOT NULL AND next_attempt_at <= :now
                  AND (locked_until IS NULL OR locked_until <= :now)
                ORDER BY next_attempt_at, id
                LIMIT :limit FOR UPDATE SKIP LOCKED
                """).setParameter("now", now).setParameter("limit", limit).getResultList())
                .stream().map(o -> o instanceof UUID u ? u : UUID.fromString(o.toString())).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        em.createNativeQuery("UPDATE notification SET locked_until = :until WHERE id IN (:ids)")
                .setParameter("until", now.plus(lease)).setParameter("ids", ids).executeUpdate();
        return springRepo.findAllById(ids).stream().map(NotificationJpaEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    @CrossTenant("a gauge over every tenant")
    public long countPending() {
        return springRepo.countByStatus(org.readybell.notification.domain.NotificationStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findByTenantAndId(TenantId tenantId, NotificationId id) {
        return springRepo.findById(id.value())
                .map(NotificationJpaEntity::toDomain);
    }
}

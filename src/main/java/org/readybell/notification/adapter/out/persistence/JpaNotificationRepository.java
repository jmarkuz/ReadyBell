package org.readybell.notification.adapter.out.persistence;

import org.readybell.shared.domain.jpa.CrossTenant;
import org.readybell.notification.domain.Notification;
import org.readybell.notification.domain.NotificationId;
import org.readybell.notification.domain.NotificationRepository;
import org.readybell.shared.domain.TenantId;
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
    public Optional<Notification> findByTenantAndId(TenantId tenantId, NotificationId id) {
        return springRepo.findById(id.value())
                .map(NotificationJpaEntity::toDomain);
    }
}

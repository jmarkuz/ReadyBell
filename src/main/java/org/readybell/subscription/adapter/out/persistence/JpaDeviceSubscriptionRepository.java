package org.readybell.subscription.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.DeviceSubscriptionId;
import org.readybell.subscription.domain.DeviceSubscriptionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing the {@link DeviceSubscriptionRepository} port.
 *
 * <h3>Tenant filter activation (Req 10.2, 10.4)</h3>
 * <p>The Hibernate {@code tenantFilter} is enabled per query before any
 * {@code SELECT}, constraining results to the current tenant so cross-tenant
 * subscriptions are excluded (Req 10.3).
 */
@Repository
@Transactional
public class JpaDeviceSubscriptionRepository implements DeviceSubscriptionRepository {

    private final DeviceSubscriptionJpaRepository springRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaDeviceSubscriptionRepository(DeviceSubscriptionJpaRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void save(DeviceSubscription subscription) {
        var entity = DeviceSubscriptionJpaEntity.from(subscription);
        springRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceSubscription> findById(DeviceSubscriptionId id) {
        return springRepo.findById(id.value())
                .map(DeviceSubscriptionJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceSubscription> findByTenantAndSession(TenantId tenantId,
                                                           CustomerSessionId sessionId) {
        enableTenantFilter(tenantId);
        return springRepo.findByTenantIdAndSessionId(tenantId.value(), sessionId.value())
                .stream()
                .map(DeviceSubscriptionJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceSubscription> findByTenantSessionAndEndpoint(TenantId tenantId,
                                                                        CustomerSessionId sessionId,
                                                                        String endpointUrl) {
        enableTenantFilter(tenantId);
        return springRepo.findByTenantIdAndSessionIdAndEndpointUrl(
                        tenantId.value(), sessionId.value(), endpointUrl)
                .map(DeviceSubscriptionJpaEntity::toDomain);
    }

    @Override
    public void delete(DeviceSubscriptionId id) {
        springRepo.deleteById(id.value());
    }

    // -------------------------------------------------------------------------
    // Tenant filter helper
    // -------------------------------------------------------------------------

    private void enableTenantFilter(TenantId tenantId) {
        entityManager.unwrap(Session.class)
                .enableFilter("tenantFilter")
                .setParameter("tenantId", tenantId.value());
    }
}

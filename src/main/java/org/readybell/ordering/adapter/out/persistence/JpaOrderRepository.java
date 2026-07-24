package org.readybell.ordering.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing the {@link OrderRepository} port.
 *
 * <h3>Tenant filter activation (Req 10.2, 10.4)</h3>
 * <p>The Hibernate {@code tenantFilter} is enabled per query before any
 * {@code SELECT} is executed, constraining results to the current tenant
 * and excluding cross-tenant rows (Req 10.3).
 *
 * <h3>Optimistic locking (Req 5.8)</h3>
 * <p>The {@code @Version} column on {@link OrderJpaEntity} ensures JPA raises an
 * {@code OptimisticLockException} when two concurrent transitions try to update the
 * same row, guaranteeing at most one succeeds.
 */
@Repository
@Transactional
public class JpaOrderRepository implements OrderRepository {

    private final OrderJpaRepository springRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaOrderRepository(OrderJpaRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void save(Order order) {
        var entity = OrderJpaEntity.from(order);
        springRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(OrderId id) {
        return springRepo.findById(id.value())
                .map(OrderJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByTenantAndId(TenantId tenantId, OrderId id) {
        enableTenantFilter(tenantId);
        return springRepo.findByIdAndTenantId(id.value(), tenantId.value())
                .map(OrderJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByTenantAndSession(TenantId tenantId, CustomerSessionId sessionId) {
        enableTenantFilter(tenantId);
        return springRepo.findByTenantIdAndSessionId(tenantId.value(), sessionId.value())
                .stream()
                .map(OrderJpaEntity::toDomain)
                .toList();
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

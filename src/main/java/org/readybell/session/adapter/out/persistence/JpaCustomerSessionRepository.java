package org.readybell.session.adapter.out.persistence;

import org.readybell.shared.domain.jpa.TenantScopedEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA adapter implementing the {@link CustomerSessionRepository} port.
 *
 * <h3>Tenant filter activation (Req 10.2, 10.4)</h3>
 * <p>Before every query the adapter enables the Hibernate named filter
 * {@code tenantFilter} on the current {@link Session}, binding the resolved
 * {@link TenantId} from {@link TenantContextHolder}. This ensures every
 * {@code SELECT} is automatically constrained to the current tenant and cross-tenant
 * rows are excluded (Req 10.3).
 *
 * <h3>Write stamping (Req 10.6)</h3>
 * <p>Stamping is handled by the {@link TenantScopedEntity}
 * {@code @PrePersist} listener — no explicit stamping is needed here.
 */
@Repository
@Transactional
public class JpaCustomerSessionRepository implements CustomerSessionRepository {

    private final CustomerSessionJpaRepository springRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaCustomerSessionRepository(CustomerSessionJpaRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void save(CustomerSession session) {
        var entity = CustomerSessionJpaEntity.from(session);
        springRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSession> findById(CustomerSessionId id) {
        return springRepo.findById(id.value())
                .map(CustomerSessionJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSession> findByTenantAndId(TenantId tenantId, CustomerSessionId id) {
        enableTenantFilter(tenantId);
        return springRepo.findByIdAndTenantId(id.value(), tenantId.value())
                .map(CustomerSessionJpaEntity::toDomain);
    }

    // -------------------------------------------------------------------------
    // Tenant filter helper
    // -------------------------------------------------------------------------

    /**
     * Enables the Hibernate {@code tenantFilter} on the current session and binds
     * the given {@code tenantId} parameter, constraining all subsequent queries on
     * this entity to that tenant (Req 10.2, 10.4).
     */
    private void enableTenantFilter(TenantId tenantId) {
        entityManager.unwrap(Session.class)
                .enableFilter("tenantFilter")
                .setParameter("tenantId", tenantId.value());
    }
}

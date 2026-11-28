package org.readybell.session.adapter.out.persistence;

import org.readybell.shared.domain.jpa.CrossTenant;
import org.readybell.shared.domain.jpa.TenantScopedEntity;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.TenantId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA adapter implementing the {@link CustomerSessionRepository} port.
 *
 * <h3>Tenant filter activation</h3>
 * <p>Before every query the adapter enables the Hibernate named filter
 * {@code tenantFilter} on the current {@link Session}, binding the resolved
 * {@link TenantId} from {@link TenantContextHolder}. This ensures every
 * {@code SELECT} is automatically constrained to the current tenant and cross-tenant
 * rows are excluded.
 *
 * <h3>Write stamping</h3>
 * <p>Stamping is handled by the {@link TenantScopedEntity}
 * {@code @PrePersist} listener — no explicit stamping is needed here.
 */
@Repository
@Transactional
public class JpaCustomerSessionRepository implements CustomerSessionRepository {
    private final CustomerSessionJpaRepository springRepo;

    public JpaCustomerSessionRepository(CustomerSessionJpaRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    @CrossTenant("the session carries its own tenant")
    public void save(CustomerSession session) {
        var entity = CustomerSessionJpaEntity.from(session);
        springRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    @CrossTenant("tenant resolution from the session id in the path")
    public Optional<CustomerSession> findById(CustomerSessionId id) {
        return springRepo.findById(id.value())
                .map(CustomerSessionJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSession> findByTenantAndId(TenantId tenantId, CustomerSessionId id) {
        return springRepo.findByIdAndTenantId(id.value(), tenantId.value())
                .map(CustomerSessionJpaEntity::toDomain);
    }
}

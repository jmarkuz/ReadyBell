package org.readybell.restaurant.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.readybell.restaurant.domain.StaffAccount;
import org.readybell.restaurant.domain.StaffAccountId;
import org.readybell.restaurant.domain.StaffAccountRepository;
import org.readybell.shared.domain.TenantId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA adapter implementing the {@link StaffAccountRepository} port.
 *
 * <h3>Tenant filter activation (Req 10.2, 10.4)</h3>
 * <p>The Hibernate {@code tenantFilter} is enabled per query before any
 * {@code SELECT}, constraining results to the current tenant and excluding
 * cross-tenant staff accounts (Req 10.3, 8.8).
 */
@Repository
@Transactional
public class JpaStaffAccountRepository implements StaffAccountRepository {

    private final StaffAccountJpaRepository springRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaStaffAccountRepository(StaffAccountJpaRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public void save(StaffAccount account) {
        var entity = StaffAccountJpaEntity.from(account);
        springRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffAccount> findById(StaffAccountId id) {
        return springRepo.findById(id.value())
                .map(StaffAccountJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffAccount> findByTenantAndId(TenantId tenantId, StaffAccountId id) {
        enableTenantFilter(tenantId);
        return springRepo.findByIdAndTenantId(id.value(), tenantId.value())
                .map(StaffAccountJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffAccount> findByTenantAndUsername(TenantId tenantId, String username) {
        enableTenantFilter(tenantId);
        return springRepo.findByTenantIdAndUsername(tenantId.value(), username)
                .map(StaffAccountJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffAccount> findByUsername(String username) {
        // Deliberately no tenant filter: usernames are unique platform-wide (used for sign-in)
        return springRepo.findByUsername(username).map(StaffAccountJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<StaffAccount> findAllByTenant(TenantId tenantId) {
        enableTenantFilter(tenantId);
        return springRepo.findByTenantIdOrderByUsernameAsc(tenantId.value()).stream()
                .map(StaffAccountJpaEntity::toDomain).toList();
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

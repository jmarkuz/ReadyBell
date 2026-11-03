package org.readybell.session.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link CustomerSessionJpaEntity}.
 *
 * <p>The tenant filter is enabled by the adapter ({@link JpaCustomerSessionRepository})
 * before any query is executed, so these derived-query methods automatically return
 * only rows that pass the {@code tenantFilter} condition (Req 10.2, 10.4).
 */
public interface CustomerSessionJpaRepository
        extends JpaRepository<CustomerSessionJpaEntity, UUID> {

    /**
     * Finds a session by its identity constrained to the given tenant.
     *
     * <p>With the Hibernate {@code tenantFilter} enabled, the database-level WHERE
     * clause automatically restricts results to the current tenant, so this query is
     * equivalent to a cross-tenant safe lookup (Req 10.3).
     *
     * @param id       the session UUID
     * @param tenantId the tenant discriminator
     * @return the session entity, or empty if not found or cross-tenant
     */
    Optional<CustomerSessionJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}

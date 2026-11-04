package org.readybell.restaurant.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link StaffAccountJpaEntity}.
 *
 * <p>The Hibernate {@code tenantFilter} is enabled by the adapter
 * ({@link JpaStaffAccountRepository}) before any query, so these derived-query
 * methods automatically return only rows matching the current tenant (Req 10.2, 10.4).
 */
public interface StaffAccountJpaRepository
        extends JpaRepository<StaffAccountJpaEntity, UUID> {

    /**
     * Finds a staff account by identity constrained to the given tenant.
     *
     * @param id       the account UUID
     * @param tenantId the tenant discriminator
     * @return the account entity, or empty if not found or cross-tenant
     */
    Optional<StaffAccountJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Finds a staff account by username within a specific tenant (Req 8.4).
     *
     * @param tenantId the tenant discriminator
     * @param username the account username
     * @return the account entity, or empty if not found
     */
    Optional<StaffAccountJpaEntity> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<StaffAccountJpaEntity> findByUsername(String username);

    java.util.List<StaffAccountJpaEntity> findByTenantIdOrderByUsernameAsc(UUID tenantId);
}

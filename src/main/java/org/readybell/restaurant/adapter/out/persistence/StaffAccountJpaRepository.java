package org.readybell.restaurant.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

/**
 * Spring Data JPA repository for {@link StaffAccountJpaEntity}.
 *
 * <p>The Hibernate {@code tenantFilter} is enabled by the adapter
 * ({@link JpaStaffAccountRepository}) before any query, so these derived-query
 * methods automatically return only rows matching the current tenant.
 */
public interface StaffAccountJpaRepository
        extends JpaRepository<StaffAccountJpaEntity, UUID> {
    /** Finds a staff account by identity constrained to the given tenant. */
    Optional<StaffAccountJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Finds a staff account by username within a specific tenant. */
    Optional<StaffAccountJpaEntity> findByTenantIdAndUsername(UUID tenantId, String username);

    List<StaffAccountJpaEntity> findByTenantIdOrderByUsernameAsc(UUID tenantId);
}

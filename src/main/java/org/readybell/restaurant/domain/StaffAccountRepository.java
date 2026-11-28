package org.readybell.restaurant.domain;

import org.readybell.shared.domain.TenantId;

import java.util.Optional;
import java.util.List;

/**
 * Repository port for {@link StaffAccount} aggregates.
 *
 * <p>This is an outbound port defined in the domain layer. Production
 * adapters (JPA, tenant-filtered) implement it in the adapter layer; in-memory fakes
 * implement it in tests.
 */
public interface StaffAccountRepository {
    void save(StaffAccount account);

    /** Finds a staff account by its identity, regardless of tenant. */
    Optional<StaffAccount> findById(StaffAccountId id);

    /** Finds a staff account by identity, constrained to the given tenant. */
    Optional<StaffAccount> findByTenantAndId(TenantId tenantId, StaffAccountId id);

    /** Finds a staff account by username within a specific tenant. */
    Optional<StaffAccount> findByTenantAndUsername(TenantId tenantId, String username);

    /** All accounts of the tenant, oldest username first. */
    List<StaffAccount> findAllByTenant(TenantId tenantId);
}

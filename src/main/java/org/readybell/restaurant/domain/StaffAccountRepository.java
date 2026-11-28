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
 *
 * <p>All query methods that accept a {@link TenantId} parameter enforce tenant isolation: they only return accounts whose {@code tenantId} matches the supplied
 * value, guaranteeing cross-tenant records are never exposed.
 */
public interface StaffAccountRepository {
    void save(StaffAccount account);

    /**
     * Finds a staff account by its identity, regardless of tenant.
     *
     * <p>Intended for internal / trusted lookups. Prefer
     * {@link #findByTenantAndId(TenantId, StaffAccountId)} for tenant-scoped access.
     */
    Optional<StaffAccount> findById(StaffAccountId id);

    /**
     * Finds a staff account by identity, constrained to the given tenant.
     *
     * <p>Returns {@link Optional#empty()} when no account with that id exists
     * <em>or</em> when the account belongs to a different tenant.
     */
    Optional<StaffAccount> findByTenantAndId(TenantId tenantId, StaffAccountId id);

    /**
     * Finds a staff account by username within a specific tenant.
     *
     * <p>Usernames are unique per tenant (not platform-wide). Used for sign-in, which names the
     * restaurant, and to enforce uniqueness before creating a new account.
     */
    Optional<StaffAccount> findByTenantAndUsername(TenantId tenantId, String username);

    /** All accounts of the tenant, oldest username first. */
    List<StaffAccount> findAllByTenant(TenantId tenantId);
}

package com.example.restaurant.domain;

import com.example.shared.domain.TenantId;

import java.util.Optional;

/**
 * Repository port for {@link StaffAccount} aggregates.
 *
 * <p>This is an outbound port defined in the domain layer (Req 11.1, 11.2). Production
 * adapters (JPA, tenant-filtered) implement it in the adapter layer; in-memory fakes
 * implement it in tests.
 *
 * <p>All query methods that accept a {@link TenantId} parameter enforce tenant isolation
 * (Req 10.2, 10.4): they only return accounts whose {@code tenantId} matches the supplied
 * value, guaranteeing cross-tenant records are never exposed (Req 8.8).
 */
public interface StaffAccountRepository {

    /**
     * Persists a new or updated {@link StaffAccount}.
     *
     * @param account the account to save (must not be null)
     */
    void save(StaffAccount account);

    /**
     * Finds a staff account by its identity, regardless of tenant.
     *
     * <p>Intended for internal / trusted lookups. Prefer
     * {@link #findByTenantAndId(TenantId, StaffAccountId)} for tenant-scoped access.
     *
     * @param id the account identity (must not be null)
     * @return the account, or {@link Optional#empty()} if not found
     */
    Optional<StaffAccount> findById(StaffAccountId id);

    /**
     * Finds a staff account by identity, constrained to the given tenant (Req 10.2, 10.4).
     *
     * <p>Returns {@link Optional#empty()} when no account with that id exists
     * <em>or</em> when the account belongs to a different tenant (Req 10.3, 8.8).
     *
     * @param tenantId the tenant scope (must not be null)
     * @param id       the account identity (must not be null)
     * @return the account, or empty if not found / belongs to another tenant
     */
    Optional<StaffAccount> findByTenantAndId(TenantId tenantId, StaffAccountId id);

    /**
     * Finds a staff account by username within a specific tenant (Req 8.4).
     *
     * <p>Used to enforce uniqueness of usernames within a tenant before creating a
     * new account.
     *
     * @param tenantId the tenant scope (must not be null)
     * @param username the account username to look up (must not be null)
     * @return the account, or empty if no account with that username exists in the tenant
     */
    Optional<StaffAccount> findByTenantAndUsername(TenantId tenantId, String username);
}

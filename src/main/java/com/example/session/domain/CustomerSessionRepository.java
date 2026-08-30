package com.example.session.domain;

import com.example.shared.domain.TenantId;

import java.util.Optional;

/**
 * Repository port for {@link CustomerSession} aggregates.
 *
 * <p>This is an outbound port defined in the domain layer (Req 11.1, 11.2). Production
 * adapters (JPA, tenant-filtered) implement it in the adapter layer; in-memory fakes
 * implement it in tests. The domain and application layers depend only on this interface.
 *
 * <p>All query methods that accept a {@link TenantId} parameter enforce tenant isolation
 * (Req 10.2, 10.4): they only return sessions whose {@code tenantId} matches the supplied
 * value, guaranteeing cross-tenant records are never exposed.
 */
public interface CustomerSessionRepository {

    /**
     * Persists a new or updated {@link CustomerSession}.
     *
     * @param session the session to save (must not be null)
     */
    void save(CustomerSession session);

    /**
     * Finds a session by its identity, regardless of tenant.
     *
     * <p>Intended for internal / trusted lookups. Prefer
     * {@link #findByTenantAndId(TenantId, CustomerSessionId)} for tenant-scoped access.
     *
     * @param id the session identity (must not be null)
     * @return the session, or {@link Optional#empty()} if not found
     */
    Optional<CustomerSession> findById(CustomerSessionId id);

    /**
     * Finds a session by identity, constrained to the given tenant (Req 10.2, 10.4).
     *
     * <p>Returns {@link Optional#empty()} when no session with that id exists
     * <em>or</em> when the session belongs to a different tenant — cross-tenant records
     * are silently excluded (Req 10.3).
     *
     * @param tenantId the tenant scope (must not be null)
     * @param id       the session identity (must not be null)
     * @return the session, or empty if not found / belongs to another tenant
     */
    Optional<CustomerSession> findByTenantAndId(TenantId tenantId, CustomerSessionId id);
}

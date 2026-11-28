package org.readybell.session.domain;

import org.readybell.shared.domain.TenantId;

import java.util.Optional;

/**
 * Repository port for {@link CustomerSession} aggregates.
 *
 * <p>This is an outbound port defined in the domain layer. Production
 * adapters (JPA, tenant-filtered) implement it in the adapter layer; in-memory fakes
 * implement it in tests. The domain and application layers depend only on this interface.
 *
 * <p>All query methods that accept a {@link TenantId} parameter enforce tenant isolation: they only return sessions whose {@code tenantId} matches the supplied
 * value, guaranteeing cross-tenant records are never exposed.
 */
public interface CustomerSessionRepository {
    void save(CustomerSession session);

    /**
     * Finds a session by its identity, regardless of tenant.
     *
     * <p>Intended for internal / trusted lookups. Prefer
     * {@link #findByTenantAndId(TenantId, CustomerSessionId)} for tenant-scoped access.
     */
    Optional<CustomerSession> findById(CustomerSessionId id);

    /**
     * Finds a session by identity, constrained to the given tenant.
     *
     * <p>Returns {@link Optional#empty()} when no session with that id exists
     * <em>or</em> when the session belongs to a different tenant — cross-tenant records
     * are silently excluded.
     */
    Optional<CustomerSession> findByTenantAndId(TenantId tenantId, CustomerSessionId id);
}

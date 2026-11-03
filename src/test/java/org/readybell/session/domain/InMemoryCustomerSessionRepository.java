package org.readybell.session.domain;

import org.readybell.shared.domain.TenantId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fake implementation of {@link CustomerSessionRepository}.
 *
 * <p>Backed by a simple {@link HashMap}; intended for use in unit and property-based
 * tests that exercise application logic without any I/O. No Spring or JPA annotations.
 *
 * <p>Tenant isolation is enforced in the tenant-scoped query method
 * ({@link #findByTenantAndId}): if the stored session's {@link TenantId} does not match
 * the requested one the session is treated as absent, mirroring the behaviour of the
 * production Hibernate-filter adapter (Req 10.2, 10.4).
 */
public class InMemoryCustomerSessionRepository implements CustomerSessionRepository {

    private final Map<CustomerSessionId, CustomerSession> store = new HashMap<>();

    @Override
    public void save(CustomerSession session) {
        store.put(session.getId(), session);
    }

    @Override
    public Optional<CustomerSession> findById(CustomerSessionId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<CustomerSession> findByTenantAndId(TenantId tenantId, CustomerSessionId id) {
        return Optional.ofNullable(store.get(id))
                .filter(session -> session.getTenantId().equals(tenantId));
    }

    /** Convenience helper — returns the total number of stored sessions. */
    public int size() {
        return store.size();
    }

    /** Clears all stored sessions. Useful for test set-up/tear-down. */
    public void clear() {
        store.clear();
    }
}

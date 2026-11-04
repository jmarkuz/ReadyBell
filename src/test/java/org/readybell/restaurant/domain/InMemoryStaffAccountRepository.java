package org.readybell.restaurant.domain;

import org.readybell.shared.domain.TenantId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fake implementation of {@link StaffAccountRepository}.
 *
 * <p>Backed by a simple {@link HashMap}; intended for use in unit and property-based
 * tests that exercise application logic without any I/O. No Spring or JPA annotations.
 *
 * <p>Tenant isolation is enforced: {@link #findByTenantAndId} and
 * {@link #findByTenantAndUsername} only return accounts whose {@link TenantId} matches the
 * requested one, mirroring the behaviour of the production Hibernate-filter adapter
 * (Req 10.2, 10.4, 8.8).
 */
public class InMemoryStaffAccountRepository implements StaffAccountRepository {

    /** Primary index: accountId → StaffAccount. */
    private final Map<StaffAccountId, StaffAccount> store = new HashMap<>();

    @Override
    public void save(StaffAccount account) {
        store.put(account.getId(), account);
    }

    @Override
    public Optional<StaffAccount> findById(StaffAccountId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<StaffAccount> findByTenantAndId(TenantId tenantId, StaffAccountId id) {
        return Optional.ofNullable(store.get(id))
                .filter(account -> account.getTenantId().equals(tenantId));
    }

    @Override
    public Optional<StaffAccount> findByTenantAndUsername(TenantId tenantId, String username) {
        return store.values().stream()
                .filter(account -> account.getTenantId().equals(tenantId)
                        && account.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public Optional<StaffAccount> findByUsername(String username) {
        return store.values().stream()
                .filter(account -> account.getUsername().equals(username))
                .findFirst();
    }

    /** Convenience helper — returns the total number of stored accounts. */
    @Override
    public java.util.List<StaffAccount> findAllByTenant(TenantId tenantId) {
        return store.values().stream().filter(a -> a.getTenantId().equals(tenantId))
                .sorted(java.util.Comparator.comparing(StaffAccount::getUsername)).toList();
    }

    public int size() {
        return store.size();
    }

    /** Clears all stored accounts. Useful for test set-up/tear-down. */
    public void clear() {
        store.clear();
    }
}

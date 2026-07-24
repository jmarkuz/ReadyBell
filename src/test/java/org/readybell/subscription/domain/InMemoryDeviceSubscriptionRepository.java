package org.readybell.subscription.domain;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fake implementation of {@link DeviceSubscriptionRepository}.
 *
 * <p>Backed by a simple {@link HashMap}; intended for use in unit and property-based
 * tests that exercise application logic without any I/O. No Spring or JPA annotations.
 *
 * <p>Tenant isolation is enforced: all tenant-scoped query methods only return
 * subscriptions whose {@link TenantId} matches the requested one, mirroring the behaviour
 * of the production Hibernate-filter adapter (Req 10.2, 10.4).
 */
public class InMemoryDeviceSubscriptionRepository implements DeviceSubscriptionRepository {

    private final Map<DeviceSubscriptionId, DeviceSubscription> store = new HashMap<>();

    @Override
    public void save(DeviceSubscription subscription) {
        store.put(subscription.getId(), subscription);
    }

    @Override
    public Optional<DeviceSubscription> findById(DeviceSubscriptionId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<DeviceSubscription> findByTenantAndSession(
            TenantId tenantId, CustomerSessionId sessionId) {
        return store.values().stream()
                .filter(sub -> sub.getTenantId().equals(tenantId)
                            && sub.getSessionId().equals(sessionId))
                .toList();
    }

    @Override
    public Optional<DeviceSubscription> findByTenantSessionAndEndpoint(
            TenantId tenantId, CustomerSessionId sessionId, String endpointUrl) {
        return store.values().stream()
                .filter(sub -> sub.getTenantId().equals(tenantId)
                            && sub.getSessionId().equals(sessionId)
                            && sub.equalsEndpoint(endpointUrl))
                .findFirst();
    }

    @Override
    public void delete(DeviceSubscriptionId id) {
        store.remove(id);
    }

    /** Convenience helper — returns the total number of stored subscriptions. */
    public int size() {
        return store.size();
    }

    /** Clears all stored subscriptions. Useful for test set-up/tear-down. */
    public void clear() {
        store.clear();
    }
}

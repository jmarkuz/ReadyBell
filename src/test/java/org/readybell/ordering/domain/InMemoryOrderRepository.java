package org.readybell.ordering.domain;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fake implementation of {@link OrderRepository}.
 *
 * <p>Backed by a simple {@link HashMap}; intended for use in unit and property-based
 * tests that exercise application logic without any I/O. No Spring or JPA annotations.
 *
 * <p>Tenant isolation is enforced: {@link #findByTenantAndId} and
 * {@link #findByTenantAndSession} only return orders whose {@link TenantId} matches the
 * requested one, mirroring the behaviour of the production Hibernate-filter adapter
 * (Req 10.2, 10.4).
 */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<OrderId, Order> store = new HashMap<>();

    @Override
    public void save(Order order) {
        store.put(order.getId(), order);
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Order> findByTenantAndId(TenantId tenantId, OrderId id) {
        return Optional.ofNullable(store.get(id))
                .filter(order -> order.getTenantId().equals(tenantId));
    }

    @Override
    public List<Order> findByTenantAndSession(TenantId tenantId, CustomerSessionId sessionId) {
        return store.values().stream()
                .filter(order -> order.getTenantId().equals(tenantId)
                              && order.getSessionId().equals(sessionId))
                .toList();
    }

    @Override
    public List<Order> findAllByTenant(TenantId tenantId) {
        return store.values().stream()
                .filter(order -> order.getTenantId().equals(tenantId))
                .toList();
    }

    /** Convenience helper — returns the total number of stored orders. */
    public int size() {
        return store.size();
    }

    /** Clears all stored orders. Useful for test set-up/tear-down. */
    public void clear() {
        store.clear();
    }
}

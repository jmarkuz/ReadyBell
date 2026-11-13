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
    public Optional<Order> findByTenantAndNumber(TenantId tenantId, int orderNumber) {
        return store.values().stream()
                .filter(order -> order.getTenantId().equals(tenantId) && order.getOrderNumber() == orderNumber)
                .findFirst();
    }

    @Override
    public List<Order> findAllByTenant(TenantId tenantId) {
        return store.values().stream()
                .filter(order -> order.getTenantId().equals(tenantId))
                .toList();
    }

    /** Convenience helper — returns the total number of stored orders. */
    @Override
    public OrderPage findPage(TenantId tenantId, OrderQuery query) {
        var all = findAllByTenant(tenantId).stream()
                .filter(o -> query.statuses().isEmpty() || query.statuses().contains(o.getStatus()))
                .filter(o -> query.from() == null || !o.getCreatedAt().isBefore(query.from()))
                .filter(o -> query.to() == null || !o.getCreatedAt().isAfter(query.to()))
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .toList();
        int from = Math.min((query.page() - 1) * query.pageSize(), all.size());
        int to = Math.min(from + query.pageSize(), all.size());
        return new OrderPage(all.subList(from, to), all.size(), query.page(), query.pageSize());
    }

    @Override
    public OrderStatusCounts countByStatus(TenantId tenantId) {
        return new OrderStatusCounts(findAllByTenant(tenantId).stream()
                .collect(java.util.stream.Collectors.groupingBy(Order::getStatus, java.util.stream.Collectors.counting())));
    }

    @Override
    public List<Order> findUpdatedSince(TenantId tenantId, java.time.Instant since, int limit) {
        return findAllByTenant(tenantId).stream()
                .filter(o -> since == null || !o.getUpdatedAt().isBefore(since))
                .limit(limit)
                .toList();
    }

    public int size() {
        return store.size();
    }

    /** Clears all stored orders. Useful for test set-up/tear-down. */
    public void clear() {
        store.clear();
    }
}

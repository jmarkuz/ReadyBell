package org.readybell.ordering.domain;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

/**
 * Repository port for {@link Order} aggregates.
 *
 * <p>This is an outbound port defined in the domain layer. Production
 * adapters (JPA, tenant-filtered) implement it in the adapter layer; in-memory fakes
 * implement it in tests.
 *
 * <p>All query methods that accept a {@link TenantId} parameter enforce tenant isolation: they only return orders whose {@code tenantId} matches the supplied
 * value, guaranteeing cross-tenant records are never exposed.
 */
public interface OrderRepository {
    void save(Order order);

    /**
     * Finds an order by its identity, regardless of tenant.
     *
     * <p>Intended for internal / trusted lookups. Prefer
     * {@link #findByTenantAndId(TenantId, OrderId)} for tenant-scoped access.
     */
    Optional<Order> findById(OrderId id);

    /**
     * Finds an order by identity, constrained to the given tenant.
     *
     * <p>Returns {@link Optional#empty()} when no order with that id exists
     * <em>or</em> when the order belongs to a different tenant.
     */
    Optional<Order> findByTenantAndId(TenantId tenantId, OrderId id);

    /**
     * Returns all orders belonging to the given tenant and associated with the given
     * customer session.
     *
     * <p>Returns an empty list when the session has no orders for this tenant.
     */
    List<Order> findByTenantAndSession(TenantId tenantId, CustomerSessionId sessionId);

    /**
     * Returns all orders belonging to the given tenant.
     *
     * <p>Used by the Restaurant Staff panel to list all orders for the authenticated
     * tenant. Returns an empty list when the tenant has no orders.
     */
    List<Order> findAllByTenant(TenantId tenantId);

    /**
     * Returns one page of the tenant's orders matching {@code query}.
     * Tenant isolation applies exactly as for the other finders.
     */
    OrderPage findPage(TenantId tenantId, OrderQuery query);

    /** Counts the tenant's orders per status with one aggregate query. */
    OrderStatusCounts countByStatus(TenantId tenantId);

    /**
     * Returns the tenant's orders created or updated at or after {@code since}, newest update first,
     * capped at {@code limit}. A {@code null} {@code since} returns the most recently updated orders.
     */
    List<Order> findUpdatedSince(TenantId tenantId, Instant since, int limit);
}

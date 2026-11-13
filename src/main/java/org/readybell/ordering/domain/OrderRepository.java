package org.readybell.ordering.domain;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

/**
 * Repository port for {@link Order} aggregates.
 *
 * <p>This is an outbound port defined in the domain layer (Req 11.1, 11.2). Production
 * adapters (JPA, tenant-filtered) implement it in the adapter layer; in-memory fakes
 * implement it in tests.
 *
 * <p>All query methods that accept a {@link TenantId} parameter enforce tenant isolation
 * (Req 10.2, 10.4): they only return orders whose {@code tenantId} matches the supplied
 * value, guaranteeing cross-tenant records are never exposed.
 */
public interface OrderRepository {

    /**
     * Persists a new or updated {@link Order}.
     *
     * @param order the order to save (must not be null)
     */
    void save(Order order);

    /**
     * Finds an order by its identity, regardless of tenant.
     *
     * <p>Intended for internal / trusted lookups. Prefer
     * {@link #findByTenantAndId(TenantId, OrderId)} for tenant-scoped access.
     *
     * @param id the order identity (must not be null)
     * @return the order, or {@link Optional#empty()} if not found
     */
    Optional<Order> findById(OrderId id);

    /**
     * Finds an order by identity, constrained to the given tenant (Req 10.2, 10.4).
     *
     * <p>Returns {@link Optional#empty()} when no order with that id exists
     * <em>or</em> when the order belongs to a different tenant (Req 10.3).
     *
     * @param tenantId the tenant scope (must not be null)
     * @param id       the order identity (must not be null)
     * @return the order, or empty if not found / belongs to another tenant
     */
    Optional<Order> findByTenantAndId(TenantId tenantId, OrderId id);

    /**
     * Returns all orders belonging to the given tenant and associated with the given
     * customer session (Req 10.2, 10.4).
     *
     * <p>Returns an empty list when the session has no orders for this tenant.
     *
     * @param tenantId  the tenant scope (must not be null)
     * @param sessionId the customer session (must not be null)
     * @return immutable list of matching orders, possibly empty
     */
    List<Order> findByTenantAndSession(TenantId tenantId, CustomerSessionId sessionId);

    /**
     * Finds the restaurant's order with the given sequential number (the number printed on the receipt).
     *
     * @param tenantId    the tenant scope (must not be null)
     * @param orderNumber the per-restaurant order number
     * @return the order, or {@link Optional#empty()} if the restaurant has none with that number
     */
    Optional<Order> findByTenantAndNumber(TenantId tenantId, int orderNumber);

    /**
     * Returns all orders belonging to the given tenant (Req 7.3, 7.7, 10.2, 10.4).
     *
     * <p>Used by the Restaurant Staff panel to list all orders for the authenticated
     * tenant. Returns an empty list when the tenant has no orders.
     *
     * @param tenantId the tenant scope (must not be null)
     * @return immutable list of all orders for the tenant, possibly empty
     */
    List<Order> findAllByTenant(TenantId tenantId);

    /**
     * Returns one page of the tenant's orders matching {@code query}.
     * Tenant isolation applies exactly as for the other finders.
     */
    OrderPage findPage(TenantId tenantId, OrderQuery query);

    /**
     * Counts the tenant's orders per status with one aggregate query (Req 10.2, 10.4).
     *
     * @param tenantId the tenant scope (must not be null)
     * @return the counts; every status is present, zero when it has no orders
     */
    OrderStatusCounts countByStatus(TenantId tenantId);

    /**
     * Returns the tenant's orders created or updated at or after {@code since}, newest update first,
     * capped at {@code limit}. A {@code null} {@code since} returns the most recently updated orders.
     */
    List<Order> findUpdatedSince(TenantId tenantId, Instant since, int limit);
}

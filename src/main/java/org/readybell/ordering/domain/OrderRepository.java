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
 */
public interface OrderRepository {
    void save(Order order);

    /** Finds an order by its identity, regardless of tenant. */
    Optional<Order> findById(OrderId id);

    /** Finds an order by identity, constrained to the given tenant. */
    Optional<Order> findByTenantAndId(TenantId tenantId, OrderId id);

    /** Returns all orders belonging to the given tenant and associated with the given customer session. */
    List<Order> findByTenantAndSession(TenantId tenantId, CustomerSessionId sessionId);

    /** Returns all orders belonging to the given tenant. */
    List<Order> findAllByTenant(TenantId tenantId);

    /** Returns one page of the tenant's orders matching {@code query}. */
    OrderPage findPage(TenantId tenantId, OrderQuery query);

    /** Counts the tenant's orders per status with one aggregate query. */
    OrderStatusCounts countByStatus(TenantId tenantId);

    /** Returns the tenant's orders created or updated at or after {@code since}, newest update first, capped at {@code limit}. */
    List<Order> findUpdatedSince(TenantId tenantId, Instant since, int limit);
}

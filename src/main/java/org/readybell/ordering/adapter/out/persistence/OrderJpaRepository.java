package org.readybell.ordering.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OrderJpaEntity}.
 *
 * <p>The Hibernate {@code tenantFilter} is enabled by the adapter
 * ({@link JpaOrderRepository}) before any query, so these derived-query methods
 * automatically return only rows that pass the tenant condition (Req 10.2, 10.4).
 */
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID>, JpaSpecificationExecutor<OrderJpaEntity> {

    /**
     * Finds an order by identity constrained to the given tenant.
     *
     * @param id       the order UUID
     * @param tenantId the tenant discriminator
     * @return the order entity, or empty if not found or cross-tenant
     */
    Optional<OrderJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Returns all orders for the given tenant and session.
     *
     * @param tenantId  the tenant discriminator
     * @param sessionId the customer session UUID
     * @return list of matching order entities, possibly empty
     */
    List<OrderJpaEntity> findByTenantIdAndSessionId(UUID tenantId, UUID sessionId);

    /**
     * Returns all orders for the given tenant.
     *
     * @param tenantId the tenant discriminator
     * @return list of all order entities for the tenant, possibly empty
     */
    List<OrderJpaEntity> findByTenantId(UUID tenantId);

    /**
     * Counts the tenant's orders per status in a single grouped query.
     *
     * @param tenantId the tenant discriminator
     * @return one {@code [OrderStatus, Long]} row per status that has orders
     */
    @Query("select o.status, count(o) from OrderJpaEntity o where o.tenantId = :tenantId group by o.status")
    List<Object[]> countGroupedByStatus(@Param("tenantId") UUID tenantId);
}

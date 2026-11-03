package org.readybell.subscription.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link DeviceSubscriptionJpaEntity}.
 *
 * <p>The Hibernate {@code tenantFilter} is enabled by the adapter
 * ({@link JpaDeviceSubscriptionRepository}) before any query, so these derived-query
 * methods automatically return only rows matching the current tenant (Req 10.2, 10.4).
 */
public interface DeviceSubscriptionJpaRepository
        extends JpaRepository<DeviceSubscriptionJpaEntity, UUID> {

    /**
     * Returns all subscriptions for the given tenant and session.
     *
     * @param tenantId  the tenant discriminator
     * @param sessionId the customer session UUID
     * @return list of matching subscription entities, possibly empty
     */
    List<DeviceSubscriptionJpaEntity> findByTenantIdAndSessionId(UUID tenantId, UUID sessionId);

    /**
     * Finds the subscription for a specific tenant, session, and endpoint URL.
     *
     * @param tenantId    the tenant discriminator
     * @param sessionId   the customer session UUID
     * @param endpointUrl the push endpoint URL
     * @return the matching entity, or empty if not found
     */
    Optional<DeviceSubscriptionJpaEntity> findByTenantIdAndSessionIdAndEndpointUrl(
            UUID tenantId, UUID sessionId, String endpointUrl);
}

package org.readybell.subscription.domain;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for {@link DeviceSubscription} aggregates.
 *
 * <p>This is an outbound port defined in the domain layer. Production
 * adapters (JPA, tenant-filtered) implement it in the adapter layer; in-memory fakes
 * implement it in tests.
 *
 * <p>All query methods that accept a {@link TenantId} parameter enforce tenant isolation: they only return subscriptions whose {@code tenantId} matches the
 * supplied value, guaranteeing cross-tenant records are never exposed.
 */
public interface DeviceSubscriptionRepository {
    void save(DeviceSubscription subscription);

    /** Finds a subscription by its identity, regardless of tenant. */
    Optional<DeviceSubscription> findById(DeviceSubscriptionId id);

    /**
     * Returns all subscriptions belonging to the given tenant and associated with the
     * given customer session.
     *
     * <p>This is the primary query for the notification flow: the
     * {@code OrderReadyHandler} uses it to determine which devices to notify.
     */
    List<DeviceSubscription> findByTenantAndSession(TenantId tenantId, CustomerSessionId sessionId);

    /**
     * Finds the subscription for a specific {@code (tenant, session, endpointUrl)} triple,
     * used to detect whether an incoming registration is a duplicate.
     *
     * <p>Returns {@link Optional#empty()} when no subscription with that endpoint exists
     * for the given tenant and session, or when the subscription belongs to a different
     * tenant.
     */
    Optional<DeviceSubscription> findByTenantSessionAndEndpoint(
            TenantId tenantId, CustomerSessionId sessionId, String endpointUrl);

    /**
     * Deletes the subscription identified by {@code id}.
     *
     * <p>Called when a customer revokes push permission or when the sender
     * reports the endpoint is expired or invalid. No-op if the subscription
     * does not exist.
     */
    void delete(DeviceSubscriptionId id);
}

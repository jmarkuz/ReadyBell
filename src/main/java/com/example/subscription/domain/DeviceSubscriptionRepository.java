package com.example.subscription.domain;

import com.example.session.domain.CustomerSessionId;
import com.example.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for {@link DeviceSubscription} aggregates.
 *
 * <p>This is an outbound port defined in the domain layer (Req 11.1, 11.2). Production
 * adapters (JPA, tenant-filtered) implement it in the adapter layer; in-memory fakes
 * implement it in tests.
 *
 * <p>All query methods that accept a {@link TenantId} parameter enforce tenant isolation
 * (Req 10.2, 10.4): they only return subscriptions whose {@code tenantId} matches the
 * supplied value, guaranteeing cross-tenant records are never exposed (Req 10.3).
 */
public interface DeviceSubscriptionRepository {

    /**
     * Persists a new or updated {@link DeviceSubscription}.
     *
     * @param subscription the subscription to save (must not be null)
     */
    void save(DeviceSubscription subscription);

    /**
     * Finds a subscription by its identity, regardless of tenant.
     *
     * @param id the subscription identity (must not be null)
     * @return the subscription, or {@link Optional#empty()} if not found
     */
    Optional<DeviceSubscription> findById(DeviceSubscriptionId id);

    /**
     * Returns all subscriptions belonging to the given tenant and associated with the
     * given customer session (Req 10.2, 10.4).
     *
     * <p>This is the primary query for the notification flow: the
     * {@code OrderReadyHandler} uses it to determine which devices to notify (Req 6.1).
     *
     * @param tenantId  the tenant scope (must not be null)
     * @param sessionId the customer session (must not be null)
     * @return list of matching subscriptions, possibly empty
     */
    List<DeviceSubscription> findByTenantAndSession(TenantId tenantId, CustomerSessionId sessionId);

    /**
     * Finds the subscription for a specific {@code (tenant, session, endpointUrl)} triple,
     * used to detect whether an incoming registration is a duplicate (Req 3.4).
     *
     * <p>Returns {@link Optional#empty()} when no subscription with that endpoint exists
     * for the given tenant and session, or when the subscription belongs to a different
     * tenant (Req 10.3).
     *
     * @param tenantId    the tenant scope (must not be null)
     * @param sessionId   the customer session (must not be null)
     * @param endpointUrl the push endpoint URL to look up (must not be null)
     * @return the matching subscription, or empty if not found
     */
    Optional<DeviceSubscription> findByTenantSessionAndEndpoint(
            TenantId tenantId, CustomerSessionId sessionId, String endpointUrl);

    /**
     * Deletes the subscription identified by {@code id}.
     *
     * <p>Called when a customer revokes push permission (Req 3.5) or when the sender
     * reports the endpoint is expired or invalid (Req 3.6). No-op if the subscription
     * does not exist.
     *
     * @param id the identity of the subscription to delete (must not be null)
     */
    void delete(DeviceSubscriptionId id);
}

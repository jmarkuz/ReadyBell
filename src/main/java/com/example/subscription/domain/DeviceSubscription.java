package com.example.subscription.domain;

import com.example.session.domain.CustomerSessionId;
import com.example.shared.domain.TenantId;

import java.util.Objects;

/**
 * Aggregate representing a Web Push subscription registered by a customer device.
 *
 * <p>A subscription belongs to exactly one {@link CustomerSessionId} within one
 * {@link TenantId} and stores the {@link PushEndpoint} required to deliver push messages
 * to that device (Req 3.1, 3.2).
 *
 * <p>The {@code (sessionId, endpointUrl)} pair is intended to be unique: calling
 * {@link #register} creates a fresh subscription while {@link #updateKeys} replaces the
 * keys on an existing one when the same endpoint re-registers (Req 3.4).
 */
public class DeviceSubscription {

    private final DeviceSubscriptionId id;
    private final TenantId tenantId;
    private final CustomerSessionId sessionId;
    private PushEndpoint endpoint;

    private DeviceSubscription(
            DeviceSubscriptionId id,
            TenantId tenantId,
            CustomerSessionId sessionId,
            PushEndpoint endpoint) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
    }

    /**
     * Factory method that creates and returns a new {@code DeviceSubscription}.
     *
     * <p>The subscription is assigned a fresh {@link DeviceSubscriptionId} and is bound
     * to the supplied {@code tenantId} and {@code sessionId} (Req 3.1).
     *
     * @param tenantId  the tenant that owns this subscription
     * @param sessionId the customer session this subscription belongs to
     * @param endpoint  the push endpoint value object (validated for completeness by {@link PushEndpoint})
     * @return a new, ready-to-persist {@code DeviceSubscription}
     */
    public static DeviceSubscription register(
            TenantId tenantId,
            CustomerSessionId sessionId,
            PushEndpoint endpoint) {
        return new DeviceSubscription(DeviceSubscriptionId.newId(), tenantId, sessionId, endpoint);
    }

    /**
     * Replaces the current {@link PushEndpoint} with a new one.
     *
     * <p>Used when the same endpoint URL re-registers with refreshed keys so that the
     * existing subscription record is updated rather than duplicated (Req 3.4).
     *
     * @param newEndpoint the replacement push endpoint (must be complete and valid)
     */
    public void updateKeys(PushEndpoint newEndpoint) {
        this.endpoint = Objects.requireNonNull(newEndpoint, "newEndpoint must not be null");
    }

    /**
     * Returns {@code true} if this subscription's endpoint URL matches the given URL.
     *
     * <p>Used to detect whether an incoming registration targets the same device endpoint
     * as an already-stored subscription (Req 3.4).
     *
     * @param endpointUrl the endpoint URL to compare against
     * @return {@code true} when the URLs are equal; {@code false} otherwise
     */
    public boolean equalsEndpoint(String endpointUrl) {
        return endpoint.endpointUrl().equals(endpointUrl);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public DeviceSubscriptionId getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public CustomerSessionId getSessionId() {
        return sessionId;
    }

    public PushEndpoint getEndpoint() {
        return endpoint;
    }

    // -------------------------------------------------------------------------
    // Reconstitution — used by the JPA adapter to rebuild the aggregate from a
    // stored entity without invoking the register factory.
    // -------------------------------------------------------------------------

    /**
     * Rebuilds a {@code DeviceSubscription} from its stored state.
     *
     * <p>Intended for use by the JPA persistence adapter only; the supplied {@code id}
     * is used as-is rather than generating a fresh one.
     *
     * @param id        the persisted subscription identity
     * @param tenantId  the persisted tenant
     * @param sessionId the persisted customer session
     * @param endpoint  the persisted push endpoint
     * @return a subscription aggregate ready to use
     */
    public static DeviceSubscription reconstitute(DeviceSubscriptionId id,
                                                  TenantId tenantId,
                                                  CustomerSessionId sessionId,
                                                  PushEndpoint endpoint) {
        return new DeviceSubscription(id, tenantId, sessionId, endpoint);
    }
}

package org.readybell.subscription.application;

import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.DeviceSubscriptionId;

/**
 * Inbound port (use-case interface) for managing Web Push device subscriptions.
 *
 * <p>This interface is the boundary between inbound adapters (REST controllers) and the
 * application service that orchestrates the domain. Wiring is performed in the composition
 * root; nothing outside the application layer depends on the concrete implementation.
 */
public interface ManageSubscriptionUseCase {
    /** Registers a new device subscription or updates the keys of the existing subscription for the same {@code (tenantId, sessionId, endpointUrl)} triple. */
    DeviceSubscription registerSubscription(RegisterSubscriptionCommand command);

    /** Deletes the subscription identified by {@code id} after verifying it belongs to {@code tenantId} (,). */
    void deleteSubscription(DeviceSubscriptionId id, TenantId tenantId);

    /** Deletes the subscription identified by {@code id} without a tenant ownership check. */
    void deleteExpiredSubscription(DeviceSubscriptionId id);
}

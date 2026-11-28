package org.readybell.subscription.application;

import org.readybell.subscription.domain.PushEndpoint;
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
    /**
     * Registers a new device subscription or updates the keys of the existing subscription
     * for the same {@code (tenantId, sessionId, endpointUrl)} triple.
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Build a {@link PushEndpoint} from the
     *       command's fields — {@code PushEndpoint}'s constructor rejects missing/blank
     *       data and throws {@link IllegalArgumentException}, satisfying.</li>
     *   <li>Look up an existing subscription for the same
     *       {@code (tenantId, sessionId, endpointUrl)}.</li>
     *   <li>If found: call {@code subscription.updateKeys(newEndpoint)} and save.</li>
     *   <li>If not found: call {@link DeviceSubscription#register}
     *       and save.</li>
     * </ol>
     *
     * @throws IllegalArgumentException if any endpoint field is null or blank
     */
    DeviceSubscription registerSubscription(RegisterSubscriptionCommand command);

    /**
     * Deletes the subscription identified by {@code id} after verifying it belongs to
     * {@code tenantId} (,).
     *
     * <p>Called when a customer revokes Web Push permission. Ensures no further messages
     * are sent to the deleted subscription.
     *
     * @throws SubscriptionNotFoundException if no subscription with that id exists, or if
     *                                       its tenant differs from {@code tenantId}
     */
    void deleteSubscription(DeviceSubscriptionId id, TenantId tenantId);

    /**
     * Deletes the subscription identified by {@code id} without a tenant ownership check.
     *
     * <p>Called internally by the notification flow when the
     * {@code NotificationSender} reports that the
     * endpoint is expired or invalid. Skips the tenant verification because the caller is
     * the platform itself rather than an external request.
     */
    void deleteExpiredSubscription(DeviceSubscriptionId id);
}

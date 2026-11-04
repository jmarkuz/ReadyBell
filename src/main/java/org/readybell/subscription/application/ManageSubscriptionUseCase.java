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
 *
 * <p>Requirements covered: 3.1, 3.2, 3.4, 3.5, 3.6.
 */
public interface ManageSubscriptionUseCase {

    /**
     * Registers a new device subscription or updates the keys of the existing subscription
     * for the same {@code (tenantId, sessionId, endpointUrl)} triple (Req 3.1, 3.4).
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Build a {@link PushEndpoint} from the
     *       command's fields — {@code PushEndpoint}'s constructor rejects missing/blank
     *       data and throws {@link IllegalArgumentException}, satisfying Req 3.3.</li>
     *   <li>Look up an existing subscription for the same
     *       {@code (tenantId, sessionId, endpointUrl)}.</li>
     *   <li>If found: call {@code subscription.updateKeys(newEndpoint)} and save (Req 3.4).</li>
     *   <li>If not found: call {@link DeviceSubscription#register}
     *       and save (Req 3.1, 3.2).</li>
     * </ol>
     *
     * @param command the registration data; must contain a non-null session + tenant and
     *                all three endpoint fields
     * @return the persisted {@link DeviceSubscription} (new or updated)
     * @throws IllegalArgumentException if any endpoint field is null or blank (Req 3.3)
     */
    DeviceSubscription registerSubscription(RegisterSubscriptionCommand command);

    /**
     * Deletes the subscription identified by {@code id} after verifying it belongs to
     * {@code tenantId} (Req 3.5, Req 10.3).
     *
     * <p>Called when a customer revokes Web Push permission. Ensures no further messages
     * are sent to the deleted subscription.
     *
     * @param id       the identity of the subscription to delete
     * @param tenantId the tenant making the revocation request
     * @throws SubscriptionNotFoundException if no subscription with that id exists, or if
     *                                       its tenant differs from {@code tenantId}
     */
    void deleteSubscription(DeviceSubscriptionId id, TenantId tenantId);

    /**
     * Deletes the subscription identified by {@code id} without a tenant ownership check
     * (Req 3.6).
     *
     * <p>Called internally by the notification flow when the
     * {@code NotificationSender} reports that the
     * endpoint is expired or invalid. Skips the tenant verification because the caller is
     * the platform itself rather than an external request.
     *
     * @param id the identity of the expired/invalid subscription to delete
     */
    void deleteExpiredSubscription(DeviceSubscriptionId id);
}

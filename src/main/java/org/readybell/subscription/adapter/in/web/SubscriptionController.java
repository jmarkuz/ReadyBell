package org.readybell.subscription.adapter.in.web;

import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.readybell.subscription.application.ManageSubscriptionUseCase;
import org.readybell.subscription.application.RegisterSubscriptionCommand;
import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.DeviceSubscriptionId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

/**
 * Inbound REST adapter for Web Push subscription management.
 *
 * <p>Exposes two endpoints over HTTPS:
 * <ul>
 *   <li>{@code POST /api/sessions/{sessionId}/subscriptions} — registers a new
 *       {@link DeviceSubscription} or updates the keys of an existing one for the same
 *       {@code (session, endpoint)} pair.</li>
 *   <li>{@code DELETE /api/sessions/{sessionId}/subscriptions/{subscriptionId}} — revokes
 *       the subscription so no further Web Push messages are sent.</li>
 * </ul>
 *
 * <p>The current tenant is resolved from {@link TenantContextHolder#require()} which
 * is populated by the inbound tenant-resolution filter before this controller is reached.
 *
 * <p>This class contains no business logic; all domain work is delegated to
 * {@link ManageSubscriptionUseCase}.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/subscriptions")
public class SubscriptionController {
    private final ManageSubscriptionUseCase manageSubscriptionUseCase;

    public SubscriptionController(ManageSubscriptionUseCase manageSubscriptionUseCase) {
        this.manageSubscriptionUseCase = Objects.requireNonNull(
                manageSubscriptionUseCase, "manageSubscriptionUseCase must not be null");
    }

    // POST /api/sessions/{sessionId}/subscriptions

    /**
     * Registers a new device subscription or updates the keys of an existing one for
     * the same {@code (session, endpointUrl)} pair.
     *
     * <p>Tenant identity is resolved from the thread-bound {@link TenantContextHolder}
     * so no explicit tenant parameter is required in the URL.
     *
     * <p>Returns HTTP 201 Created with a {@link SubscriptionResponse} body containing
     * the subscription id and a {@code Location} header pointing to the newly created
     * resource.
     */
    @PostMapping
    public ResponseEntity<SubscriptionResponse> registerSubscription(
            @PathVariable UUID sessionId,
            @RequestBody RegisterSubscriptionRequest request) {
        var tenantId = TenantContextHolder.require();
        var customerSessionId = new CustomerSessionId(sessionId);

        var command = new RegisterSubscriptionCommand(
                customerSessionId,
                tenantId,
                request.endpointUrl(),
                request.p256dhKey(),
                request.authKey());

        DeviceSubscription subscription = manageSubscriptionUseCase.registerSubscription(command);

        var response = SubscriptionResponse.from(subscription);
        var location = URI.create(
                "/api/sessions/" + sessionId + "/subscriptions/" + response.subscriptionId());

        return ResponseEntity.created(location).body(response);
    }

    // DELETE /api/sessions/{sessionId}/subscriptions/{subscriptionId}

    /**
     * Revokes a device subscription so that no further Web Push messages are delivered
     * to it.
     *
     * <p>The use case verifies that the subscription belongs to the current tenant before
     * deleting it, enforcing cross-tenant isolation.
     */
    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> deleteSubscription(
            @PathVariable UUID sessionId,
            @PathVariable UUID subscriptionId) {
        var tenantId = TenantContextHolder.require();
        var subId = new DeviceSubscriptionId(subscriptionId);

        manageSubscriptionUseCase.deleteSubscription(subId, tenantId);

        return ResponseEntity.noContent().build();
    }
}

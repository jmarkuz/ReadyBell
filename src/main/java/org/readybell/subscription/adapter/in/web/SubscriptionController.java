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
 * <p>Exposes two endpoints over HTTPS (Req 3.7):
 * <ul>
 *   <li>{@code POST /api/sessions/{sessionId}/subscriptions} — registers a new
 *       {@link DeviceSubscription} or updates the keys of an existing one for the same
 *       {@code (session, endpoint)} pair (Req 3.1, 3.2, 3.4).</li>
 *   <li>{@code DELETE /api/sessions/{sessionId}/subscriptions/{subscriptionId}} — revokes
 *       the subscription so no further Web Push messages are sent (Req 3.5).</li>
 * </ul>
 *
 * <p>The current tenant is resolved from {@link TenantContextHolder#require()} which
 * is populated by the inbound tenant-resolution filter before this controller is reached
 * (Req 10.4, 10.5).
 *
 * <p>This class contains no business logic; all domain work is delegated to
 * {@link ManageSubscriptionUseCase}. Requirements covered: 3.1–3.5, 3.7.
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/subscriptions")
public class SubscriptionController {

    private final ManageSubscriptionUseCase manageSubscriptionUseCase;

    /**
     * Constructor injection — the implementation is wired by the composition root.
     *
     * @param manageSubscriptionUseCase the use-case port; must not be {@code null}
     */
    public SubscriptionController(ManageSubscriptionUseCase manageSubscriptionUseCase) {
        this.manageSubscriptionUseCase = Objects.requireNonNull(
                manageSubscriptionUseCase, "manageSubscriptionUseCase must not be null");
    }

    // -------------------------------------------------------------------------
    // POST /api/sessions/{sessionId}/subscriptions
    // -------------------------------------------------------------------------

    /**
     * Registers a new device subscription or updates the keys of an existing one for
     * the same {@code (session, endpointUrl)} pair (Req 3.1, 3.4).
     *
     * <p>Tenant identity is resolved from the thread-bound {@link TenantContextHolder}
     * so no explicit tenant parameter is required in the URL (Req 10.4).
     *
     * <p>Returns HTTP 201 Created with a {@link SubscriptionResponse} body containing
     * the subscription id and a {@code Location} header pointing to the newly created
     * resource.
     *
     * @param sessionId UUID string of the customer session (path variable)
     * @param request   JSON body with {@code endpointUrl}, {@code p256dhKey},
     *                  {@code authKey}; missing/blank fields are rejected by the domain
     *                  with {@link IllegalArgumentException} (Req 3.3)
     * @return 201 with subscription id, or an error if the session is inactive or the
     *         request is incomplete
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

        String subId = subscription.getId().value().toString();
        var response = new SubscriptionResponse(subId);
        var location = URI.create("/api/sessions/" + sessionId + "/subscriptions/" + subId);

        return ResponseEntity.created(location).body(response);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/sessions/{sessionId}/subscriptions/{subscriptionId}
    // -------------------------------------------------------------------------

    /**
     * Revokes a device subscription so that no further Web Push messages are delivered
     * to it (Req 3.5).
     *
     * <p>The use case verifies that the subscription belongs to the current tenant before
     * deleting it, enforcing cross-tenant isolation (Req 10.3).
     *
     * @param sessionId      UUID string of the customer session (path variable; carried
     *                       for URL consistency, ownership is enforced by tenant)
     * @param subscriptionId UUID string of the subscription to delete
     * @return 204 No Content on success
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

package org.readybell.subscription.application;

import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.DeviceSubscriptionId;
import org.readybell.subscription.domain.DeviceSubscriptionRepository;
import org.readybell.subscription.domain.PushEndpoint;
import org.readybell.subscription.domain.PushEndpointPolicy;

import java.util.Objects;
import java.util.Optional;

/**
 * Application service that implements {@link ManageSubscriptionUseCase}.
 *
 * <p>Orchestrates registration, idempotent re-registration, and deletion of
 * {@link DeviceSubscription} aggregates. Uses plain constructor injection — no Spring
 * annotations are present in this class (wiring happens in the composition root).
 */
public class ManageSubscriptionService implements ManageSubscriptionUseCase {
    private final CustomerSessionRepository sessionRepository;
    private final DeviceSubscriptionRepository subscriptionRepository;

    /** A customer has a phone, maybe a tablet; anything near this is a script filling the table. */
    public static final int MAX_SUBSCRIPTIONS_PER_SESSION = 5;

    private final PushEndpointPolicy endpointPolicy;

    public ManageSubscriptionService(CustomerSessionRepository sessionRepository,
                                     DeviceSubscriptionRepository subscriptionRepository,
                                     PushEndpointPolicy endpointPolicy) {
        this.endpointPolicy = Objects.requireNonNull(endpointPolicy, "endpointPolicy must not be null");
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository, "sessionRepository must not be null");
        this.subscriptionRepository = Objects.requireNonNull(
                subscriptionRepository, "subscriptionRepository must not be null");
    }

    @Override
    public DeviceSubscription registerSubscription(RegisterSubscriptionCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        // verify the session exists and is active for this tenant.
        var session = sessionRepository
                .findByTenantAndId(command.tenantId(), command.sessionId())
                .orElseThrow(() -> new SessionNotActiveException(command.sessionId()));

        if (!session.isActive()) {
            throw new SessionNotActiveException(command.sessionId());
        }

        // only real push services over https (the server calls this URL: SSRF otherwise), bounded lengths
        // then completeness: the PushEndpoint constructor throws if any field is blank.
        endpointPolicy.validate(command.endpointUrl(), command.p256dhKey(), command.authKey());
        PushEndpoint newEndpoint = new PushEndpoint(
                command.endpointUrl(),
                command.p256dhKey(),
                command.authKey());

        // check for an existing subscription for the same (tenant, session, endpoint).
        Optional<DeviceSubscription> existing =
                subscriptionRepository.findByTenantSessionAndEndpoint(
                        command.tenantId(),
                        command.sessionId(),
                        command.endpointUrl());

        if (existing.isPresent()) {
            DeviceSubscription subscription = existing.get();
            subscription.updateKeys(newEndpoint);
            subscriptionRepository.save(subscription);
            return subscription;
        }

        // Bound the number of devices per session
        if (subscriptionRepository.findByTenantAndSession(command.tenantId(), command.sessionId()).size()
                >= MAX_SUBSCRIPTIONS_PER_SESSION) {
            throw new TooManySubscriptionsException(MAX_SUBSCRIPTIONS_PER_SESSION);
        }

        DeviceSubscription subscription = DeviceSubscription.register(
                command.tenantId(),
                command.sessionId(),
                newEndpoint);
        subscriptionRepository.save(subscription);
        return subscription;
    }

    @Override
    public void deleteSubscription(DeviceSubscriptionId id, TenantId tenantId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        DeviceSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new SubscriptionNotFoundException(id));

        if (!subscription.getTenantId().equals(tenantId)) {
            // Return not-found rather than disclosing cross-tenant existence.
            throw new SubscriptionNotFoundException(id);
        }

        subscriptionRepository.delete(id);
    }

    @Override
    public void deleteExpiredSubscription(DeviceSubscriptionId id) {
        Objects.requireNonNull(id, "id must not be null");
        // No tenant check — caller is the notification flow, not a customer.
        subscriptionRepository.delete(id);
    }
}

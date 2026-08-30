package com.example.subscription.application;

import com.example.session.domain.CustomerSessionRepository;
import com.example.shared.domain.TenantId;
import com.example.subscription.domain.DeviceSubscription;
import com.example.subscription.domain.DeviceSubscriptionId;
import com.example.subscription.domain.DeviceSubscriptionRepository;
import com.example.subscription.domain.PushEndpoint;

import java.util.Objects;
import java.util.Optional;

/**
 * Application service that implements {@link ManageSubscriptionUseCase}.
 *
 * <p>Orchestrates registration, idempotent re-registration, and deletion of
 * {@link DeviceSubscription} aggregates. Uses plain constructor injection — no Spring
 * annotations are present in this class (wiring happens in the composition root).
 *
 * <p>Requirements covered: 3.1, 3.2, 3.4, 3.5, 3.6.
 */
public class ManageSubscriptionService implements ManageSubscriptionUseCase {

    private final CustomerSessionRepository sessionRepository;
    private final DeviceSubscriptionRepository subscriptionRepository;

    /**
     * Constructor — all dependencies injected explicitly.
     *
     * @param sessionRepository      the repository port for validating customer sessions
     *                               (must not be null)
     * @param subscriptionRepository the repository port for persisting and querying
     *                               device subscriptions (must not be null)
     */
    public ManageSubscriptionService(CustomerSessionRepository sessionRepository,
                                     DeviceSubscriptionRepository subscriptionRepository) {
        this.sessionRepository = Objects.requireNonNull(
                sessionRepository, "sessionRepository must not be null");
        this.subscriptionRepository = Objects.requireNonNull(
                subscriptionRepository, "subscriptionRepository must not be null");
    }

    // -------------------------------------------------------------------------
    // ManageSubscriptionUseCase implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>First verifies the session exists and is active for the given tenant (Req 3.1).
     * Then validates the endpoint data by constructing a {@link PushEndpoint} — the
     * record's compact constructor rejects null or blank fields with
     * {@link IllegalArgumentException} (Req 3.3). Finally either updates an existing
     * subscription's keys (Req 3.4) or creates and persists a new one (Req 3.1, 3.2).
     */
    @Override
    public DeviceSubscription registerSubscription(RegisterSubscriptionCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        // Step 1 — verify the session exists and is active for this tenant (Req 3.1).
        var session = sessionRepository
                .findByTenantAndId(command.tenantId(), command.sessionId())
                .orElseThrow(() -> new SessionNotActiveException(command.sessionId()));

        if (!session.isActive()) {
            throw new SessionNotActiveException(command.sessionId());
        }

        // Step 2 — validate completeness; PushEndpoint constructor throws if any field is blank.
        PushEndpoint newEndpoint = new PushEndpoint(
                command.endpointUrl(),
                command.p256dhKey(),
                command.authKey());

        // Step 3 — check for an existing subscription for the same (tenant, session, endpoint).
        Optional<DeviceSubscription> existing =
                subscriptionRepository.findByTenantSessionAndEndpoint(
                        command.tenantId(),
                        command.sessionId(),
                        command.endpointUrl());

        if (existing.isPresent()) {
            // Step 4 — same endpoint re-registered: update keys, save, return (Req 3.4).
            DeviceSubscription subscription = existing.get();
            subscription.updateKeys(newEndpoint);
            subscriptionRepository.save(subscription);
            return subscription;
        }

        // Step 5 — new endpoint: register, save, return (Req 3.1, 3.2).
        DeviceSubscription subscription = DeviceSubscription.register(
                command.tenantId(),
                command.sessionId(),
                newEndpoint);
        subscriptionRepository.save(subscription);
        return subscription;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Loads the subscription, verifies it belongs to {@code tenantId} (Req 10.3),
     * then deletes it so no further messages are sent (Req 3.5).
     */
    @Override
    public void deleteSubscription(DeviceSubscriptionId id, TenantId tenantId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        // Step 1 — load by id.
        DeviceSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new SubscriptionNotFoundException(id));

        // Step 2 — verify the subscription belongs to the requesting tenant (Req 10.3).
        if (!subscription.getTenantId().equals(tenantId)) {
            // Return not-found rather than disclosing cross-tenant existence (Req 10.3).
            throw new SubscriptionNotFoundException(id);
        }

        // Step 3 — delete (Req 3.5).
        subscriptionRepository.delete(id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deletes the subscription unconditionally (Req 3.6). No tenant check is performed
     * because this is an internal platform call triggered by the notification flow, not an
     * external customer request.
     */
    @Override
    public void deleteExpiredSubscription(DeviceSubscriptionId id) {
        Objects.requireNonNull(id, "id must not be null");
        // No tenant check — caller is the notification flow, not a customer (Req 3.6).
        subscriptionRepository.delete(id);
    }
}

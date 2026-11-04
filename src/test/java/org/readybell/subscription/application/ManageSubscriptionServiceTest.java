package org.readybell.subscription.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.session.domain.InMemoryCustomerSessionRepository;
import org.readybell.session.domain.SessionStatus;
import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.domain.DeviceSubscriptionId;
import org.readybell.subscription.domain.InMemoryDeviceSubscriptionRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManageSubscriptionServiceTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final TenantId OTHER = new TenantId(UUID.randomUUID());

    private InMemoryCustomerSessionRepository sessions;
    private InMemoryDeviceSubscriptionRepository subscriptions;
    private ManageSubscriptionService service;
    private CustomerSession session;

    @BeforeEach
    void setUp() {
        sessions = new InMemoryCustomerSessionRepository();
        subscriptions = new InMemoryDeviceSubscriptionRepository();
        service = new ManageSubscriptionService(sessions, subscriptions);
        session = CustomerSession.start(TENANT, Clock.systemUTC());
        sessions.save(session);
    }

    private RegisterSubscriptionCommand command(String endpoint, String p256dh, String auth) {
        return new RegisterSubscriptionCommand(session.getId(), TENANT, endpoint, p256dh, auth);
    }

    // ---- registerSubscription ---------------------------------------------------------------

    @Test
    void registersANewDevice() {
        var subscription = service.registerSubscription(command("https://push/1", "key", "auth"));

        assertThat(subscription.getTenantId()).isEqualTo(TENANT);
        assertThat(subscription.getEndpoint().endpointUrl()).isEqualTo("https://push/1");
        assertThat(subscriptions.size()).isEqualTo(1);
    }

    @Test
    void sameEndpointUpdatesTheKeysInsteadOfAddingADevice() {
        var first = service.registerSubscription(command("https://push/1", "key-1", "auth-1"));

        var second = service.registerSubscription(command("https://push/1", "key-2", "auth-2"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getEndpoint().p256dhKey()).isEqualTo("key-2");
        assertThat(second.getEndpoint().authKey()).isEqualTo("auth-2");
        assertThat(subscriptions.size()).isEqualTo(1);
    }

    @Test
    void differentEndpointsAreSeparateDevices() {
        service.registerSubscription(command("https://push/1", "key", "auth"));
        service.registerSubscription(command("https://push/2", "key", "auth"));

        assertThat(subscriptions.size()).isEqualTo(2);
    }

    @Test
    void unknownSessionIsRejected() {
        var unknown = new RegisterSubscriptionCommand(new CustomerSessionId(UUID.randomUUID()), TENANT,
                "https://push/1", "key", "auth");

        assertThatThrownBy(() -> service.registerSubscription(unknown)).isInstanceOf(SessionNotActiveException.class);
        assertThat(subscriptions.size()).isZero();
    }

    @Test
    void endedSessionIsRejected() {
        var ended = CustomerSession.reconstitute(new CustomerSessionId(UUID.randomUUID()), TENANT,
                SessionStatus.ENDED, Instant.now());
        sessions.save(ended);

        assertThatThrownBy(() -> service.registerSubscription(
                new RegisterSubscriptionCommand(ended.getId(), TENANT, "https://push/1", "key", "auth")))
                .isInstanceOf(SessionNotActiveException.class);
    }

    @Test
    void anotherTenantsSessionIsRejected() {
        assertThatThrownBy(() -> service.registerSubscription(
                new RegisterSubscriptionCommand(session.getId(), OTHER, "https://push/1", "key", "auth")))
                .isInstanceOf(SessionNotActiveException.class);
    }

    @Test
    void incompleteEndpointIsRejected() {
        assertThatThrownBy(() -> service.registerSubscription(command("https://push/1", " ", "auth")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(subscriptions.size()).isZero();
    }

    @Test
    void nullCommandIsRejected() {
        assertThatThrownBy(() -> service.registerSubscription(null)).isInstanceOf(NullPointerException.class);
    }

    // ---- deleteSubscription -----------------------------------------------------------------

    @Test
    void ownerCanDeleteASubscription() {
        var subscription = service.registerSubscription(command("https://push/1", "key", "auth"));

        service.deleteSubscription(subscription.getId(), TENANT);

        assertThat(subscriptions.size()).isZero();
    }

    @Test
    void unknownSubscriptionIsNotFound() {
        assertThatThrownBy(() -> service.deleteSubscription(DeviceSubscriptionId.newId(), TENANT))
                .isInstanceOf(SubscriptionNotFoundException.class);
    }

    @Test
    void anotherTenantsSubscriptionLooksLikeItDoesNotExist() {
        var subscription = service.registerSubscription(command("https://push/1", "key", "auth"));

        assertThatThrownBy(() -> service.deleteSubscription(subscription.getId(), OTHER))
                .isInstanceOf(SubscriptionNotFoundException.class);
        assertThat(subscriptions.size()).isEqualTo(1);
    }

    @Test
    void deleteRejectsNulls() {
        assertThatThrownBy(() -> service.deleteSubscription(null, TENANT)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.deleteSubscription(DeviceSubscriptionId.newId(), null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- deleteExpiredSubscription ----------------------------------------------------------

    @Test
    void expiredSubscriptionIsRemovedWithoutATenantCheck() {
        var subscription = service.registerSubscription(command("https://push/1", "key", "auth"));

        service.deleteExpiredSubscription(subscription.getId());

        assertThat(subscriptions.size()).isZero();
    }

    @Test
    void deleteExpiredRejectsNull() {
        assertThatThrownBy(() -> service.deleteExpiredSubscription(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsNulls() {
        assertThatThrownBy(() -> new ManageSubscriptionService(null, subscriptions))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManageSubscriptionService(sessions, null))
                .isInstanceOf(NullPointerException.class);
    }
}

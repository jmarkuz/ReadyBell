package org.readybell.notification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.InMemoryNotificationRepository;
import org.readybell.notification.domain.NotificationPayload;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.notification.domain.SendResult;
import org.readybell.ordering.domain.OrderId;
import org.readybell.ordering.domain.OrderReady;
import org.readybell.restaurant.domain.InMemoryRestaurantRepository;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.RecordingDomainEventPublisher;
import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.application.ManageSubscriptionUseCase;
import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.InMemoryDeviceSubscriptionRepository;
import org.readybell.subscription.domain.PushEndpoint;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OrderReadyHandlerTest {

    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final CustomerSessionId session = CustomerSessionId.newId();
    private InMemoryDeviceSubscriptionRepository subscriptions;
    private InMemoryNotificationRepository notifications;
    private RecordingDomainEventPublisher events;
    private InMemoryRestaurantRepository restaurants;
    private final List<SendResult> script = new ArrayList<>();
    private final List<NotificationPayload> sent = new ArrayList<>();

    /** Replays {@link #script} in order; ACCEPTED once it is exhausted. */
    private final NotificationSender sender = (endpoint, payload) -> {
        sent.add(payload);
        return script.isEmpty() ? SendResult.ACCEPTED : script.remove(0);
    };

    @BeforeEach
    void setUp() {
        subscriptions = new InMemoryDeviceSubscriptionRepository();
        notifications = new InMemoryNotificationRepository();
        events = new RecordingDomainEventPublisher();
        restaurants = new InMemoryRestaurantRepository();
        restaurants.save(Restaurant.create(tenant, "aurora", RestaurantConfig.of("Café Aurora")));
        subscriptions.save(DeviceSubscription.register(tenant, session,
                new PushEndpoint("https://push.example/1", "p256dh", "auth")));
    }

    private OrderReadyHandler handler(List<Duration> retries) {
        return new OrderReadyHandler(subscriptions, notifications, sender, events,
                mock(ManageSubscriptionUseCase.class), restaurants, retries);
    }

    private OrderReady event() {
        return OrderReady.of(new OrderId(UUID.fromString("abcdef12-0000-0000-0000-000000000000")),
                tenant, session, Instant.now());
    }

    @Test
    void payloadNamesRestaurantAndOrderAndLinksToTrackingPage() {
        handler(List.of()).handle(event());

        assertEquals(1, sent.size());
        assertEquals("Café Aurora", sent.get(0).title());
        assertEquals("Order #ABCDEF12 is ready for pickup!", sent.get(0).body());
        assertEquals("/track/" + session.value(), sent.get(0).url());
    }

    @Test
    void transientFailureIsRetriedUntilAccepted() {
        script.add(SendResult.TRANSIENT_FAILURE);
        script.add(SendResult.TRANSIENT_FAILURE);

        handler(List.of(Duration.ZERO, Duration.ZERO, Duration.ZERO)).handle(event());

        assertEquals(3, sent.size());
        assertTrue(events.getPublishedEvents().stream().anyMatch(e -> e.getClass().getSimpleName().equals("NotificationSent")));
    }

    @Test
    void retriesAreBoundedAndFailureIsRecorded() {
        for (int i = 0; i < 10; i++) script.add(SendResult.TRANSIENT_FAILURE);

        handler(List.of(Duration.ZERO, Duration.ZERO)).handle(event());

        assertEquals(3, sent.size());           // first attempt + 2 retries
        assertTrue(events.hasNoPublishedEvents());
    }

    @Test
    void rejectionIsNotRetried() {
        script.add(SendResult.REJECTED);

        handler(List.of(Duration.ZERO, Duration.ZERO)).handle(event());

        assertEquals(1, sent.size());
    }
}

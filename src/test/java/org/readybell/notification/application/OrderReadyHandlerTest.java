package org.readybell.notification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.notification.domain.InMemoryNotificationRepository;
import org.readybell.notification.domain.Notification;
import org.readybell.notification.domain.NotificationStatus;
import org.readybell.ordering.domain.InMemoryOrderRepository;
import org.readybell.ordering.domain.Order;
import org.readybell.ordering.domain.OrderDetails;
import org.readybell.ordering.domain.OrderReady;
import org.readybell.restaurant.domain.InMemoryRestaurantRepository;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.InMemoryDeviceSubscriptionRepository;
import org.readybell.subscription.domain.PushEndpoint;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The handler only queues the pushes (PENDING, due at once, with a deadline) and wakes the worker: it sends nothing. */
class OrderReadyHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final Duration DEADLINE = Duration.ofMinutes(10);

    private final TenantId tenant = new TenantId(UUID.randomUUID());
    private final CustomerSessionId session = CustomerSessionId.newId();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AtomicInteger wakeUps = new AtomicInteger();
    private InMemoryDeviceSubscriptionRepository subscriptions;
    private InMemoryNotificationRepository notifications;
    private InMemoryRestaurantRepository restaurants;
    private InMemoryOrderRepository orders;
    private OrderReadyHandler handler;

    @BeforeEach
    void setUp() {
        subscriptions = new InMemoryDeviceSubscriptionRepository();
        notifications = new InMemoryNotificationRepository();
        restaurants = new InMemoryRestaurantRepository();
        orders = new InMemoryOrderRepository();
        restaurants.save(Restaurant.create(tenant, "aurora", RestaurantConfig.of("Café Aurora")));
        subscriptions.save(DeviceSubscription.register(tenant, session,
                new PushEndpoint("https://push.example/1", "p256dh", "auth")));
        handler = new OrderReadyHandler(subscriptions, notifications, restaurants, orders,
                wakeUps::incrementAndGet, DEADLINE, clock);
    }

    private OrderReady event() {
        var order = Order.create(tenant, session, clock, OrderDetails.NONE, 101);
        orders.save(order);
        return OrderReady.of(order.getId(), tenant, session, NOW);
    }

    @Test
    void queuesOnePendingPushPerDeviceAndWakesTheWorker() {
        subscriptions.save(DeviceSubscription.register(tenant, session,
                new PushEndpoint("https://push.example/2", "p256dh", "auth")));

        handler.handle(event());

        assertEquals(2, notifications.size());
        assertTrue(notifications.all().stream().allMatch(n -> n.status() == NotificationStatus.PENDING));
        assertEquals(1, wakeUps.get());
    }

    @Test
    void theQueuedPushNamesRestaurantAndOrderAndLinksToTheTrackingPage() {
        handler.handle(event());

        Notification queued = notifications.all().get(0);
        var message = queued.toMessage();
        assertEquals("Café Aurora", message.title());
        assertEquals("Order #101 is ready for pickup!", message.body());
        assertEquals("/track/" + session.value(), message.url());
    }

    @Test
    void itIsDueAtOnceAndExpiresTenMinutesAfterTheOrderBecameReady() {
        handler.handle(event());

        Notification queued = notifications.all().get(0);
        assertTrue(queued.isDue(NOW));
        assertEquals(NOW.plus(DEADLINE), queued.expiresAt());
        assertEquals(NOW, queued.readyAt());
        assertEquals(0, queued.attempts());
    }

    @Test
    void aReplayedEventDoesNotQueueASecondPush() {
        OrderReady event = event();
        handler.handle(event);
        handler.handle(event);                       // the relay delivers it again after a crash

        assertEquals(1, notifications.size());
        assertEquals(1, wakeUps.get());              // nothing new was queued the second time
    }

    @Test
    void aFailedPushIsNotRequeuedByAReplayButAnAcceptedOneIsNotRepeated() {
        OrderReady event = event();
        handler.handle(event);
        Notification queued = notifications.all().get(0);
        queued.markSent(clock);
        notifications.save(queued);

        handler.handle(event);

        assertEquals(1, notifications.size());
    }

    @Test
    void noSubscriptionsMeansNothingIsQueuedAndNoError() {
        var other = CustomerSessionId.newId();
        var order = Order.create(tenant, other, clock, OrderDetails.NONE, 5);
        orders.save(order);

        handler.handle(OrderReady.of(order.getId(), tenant, other, NOW));

        assertEquals(0, notifications.size());
        assertEquals(0, wakeUps.get());
    }

    @Test
    void anUnknownOrderStillGetsAGenericMessage() {
        handler.handle(OrderReady.of(new org.readybell.ordering.domain.OrderId(UUID.randomUUID()), tenant, session, NOW));

        assertEquals("Your order is ready for pickup!", notifications.all().get(0).toMessage().body());
        assertNull(notifications.all().get(0).lastError());
        assertFalse(notifications.all().get(0).isExpired(NOW));
    }

    // ---- cancelled orders ---------------------------------------------------------------------------------------------

    private org.readybell.ordering.domain.OrderStatusUpdated cancelled(org.readybell.ordering.domain.Order order) {
        return org.readybell.ordering.domain.OrderStatusUpdated.of(order.getId(), tenant, session,
                org.readybell.ordering.domain.OrderStatus.IN_PROGRESS, org.readybell.ordering.domain.OrderStatus.CANCELLED, NOW);
    }

    private org.readybell.ordering.domain.Order openOrder() {
        var order = Order.create(tenant, session, clock, OrderDetails.NONE, 7);
        orders.save(order);
        return order;
    }

    @Test
    void aCancelledOrderQueuesACancellationPush() {
        var order = openOrder();

        handler.handleStatusUpdated(cancelled(order));

        assertEquals(1, notifications.size());
        Notification queued = notifications.all().get(0);
        assertEquals(Notification.KIND_CANCELLED, queued.kind());
        assertTrue(queued.toMessage().body().startsWith("Order #7 was cancelled"));
        assertEquals("/track/" + session.value(), queued.toMessage().url());
        assertEquals(1, wakeUps.get());
    }

    @Test
    void otherStatusChangesQueueNothing() {
        var order = openOrder();

        handler.handleStatusUpdated(org.readybell.ordering.domain.OrderStatusUpdated.of(order.getId(), tenant, session,
                org.readybell.ordering.domain.OrderStatus.CREATED, org.readybell.ordering.domain.OrderStatus.IN_PROGRESS, NOW));

        assertEquals(0, notifications.size());
        assertEquals(0, wakeUps.get());
    }

    @Test
    void aReplayedCancellationIsNotQueuedTwice() {
        var event = cancelled(openOrder());
        handler.handleStatusUpdated(event);
        handler.handleStatusUpdated(event);

        assertEquals(1, notifications.size());
    }

    @Test
    void anOrderThatWasReadyAndIsThenCancelledGetsBothPushes() {
        var order = openOrder();
        handler.handle(OrderReady.of(order.getId(), tenant, session, NOW));
        notifications.all().get(0).markSent(clock);                 // the ready push went out

        handler.handleStatusUpdated(cancelled(order));

        assertEquals(2, notifications.size());
        assertEquals(java.util.Set.of(Notification.KIND_READY, Notification.KIND_CANCELLED),
                notifications.all().stream().map(Notification::kind).collect(java.util.stream.Collectors.toSet()));
    }
}

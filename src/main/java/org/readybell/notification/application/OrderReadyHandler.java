package org.readybell.notification.application;

import org.readybell.notification.domain.Notification;
import org.readybell.notification.domain.NotificationPayload;
import org.readybell.notification.domain.NotificationRepository;
import org.readybell.ordering.domain.OrderReady;
import org.readybell.ordering.domain.OrderRepository;
import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.DeviceSubscriptionRepository;

import java.time.Clock;
import org.readybell.restaurant.domain.RestaurantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Application-layer handler for the {@link OrderReady} domain event.
 *
 * <p>It does not send anything itself. For every device subscribed to the session it queues a delivery task (a PENDING
 * {@link Notification} due at once, with a deadline) and wakes the delivery worker; {@link PushDeliveryService} makes the
 * attempts, so a slow or failing push service holds up nobody (Req 6.1–6.8, 3.6):
 * <ol>
 *   <li>Query {@link DeviceSubscriptionRepository} for the subscriptions of the event's session and tenant (Req 6.1).</li>
 *   <li>For each, queue a notification, unless this order already has one waiting or accepted for that device (a replayed
 *       event must not push twice).</li>
 *   <li>If there are no subscriptions, record that nothing was delivered and return without error (Req 6.6).</li>
 * </ol>
 * No Spring annotations are used: wiring happens in the composition root.
 */
public class OrderReadyHandler {

    private static final Logger LOG = Logger.getLogger(OrderReadyHandler.class.getName());

    private final DeviceSubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;
    private final RestaurantRepository restaurantRepository;
    private final OrderRepository orderRepository;
    private final DeliveryTrigger deliveryTrigger;
    private final Duration deadline;
    private final Clock clock;

    /**
     * @param deadline how long after the order became READY a push is still worth sending; later attempts are dropped
     */
    public OrderReadyHandler(DeviceSubscriptionRepository subscriptionRepository,
                             NotificationRepository notificationRepository,
                             RestaurantRepository restaurantRepository,
                             OrderRepository orderRepository,
                             DeliveryTrigger deliveryTrigger,
                             Duration deadline,
                             Clock clock) {
        this.subscriptionRepository = Objects.requireNonNull(subscriptionRepository,
                "subscriptionRepository must not be null");
        this.notificationRepository = Objects.requireNonNull(notificationRepository,
                "notificationRepository must not be null");
        this.restaurantRepository = Objects.requireNonNull(restaurantRepository,
                "restaurantRepository must not be null");
        this.orderRepository = Objects.requireNonNull(orderRepository,
                "orderRepository must not be null");
        this.deliveryTrigger = Objects.requireNonNull(deliveryTrigger, "deliveryTrigger must not be null");
        this.deadline = Objects.requireNonNull(deadline, "deadline must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Queues the push of an {@link OrderReady} event for every device of the session and wakes the worker.
     *
     * @param event the order-ready event (must not be {@code null})
     */
    public void handle(OrderReady event) {
        Objects.requireNonNull(event, "event must not be null");

        // Req 6.1: subscriptions of the session, scoped to the event's tenant
        List<DeviceSubscription> subscriptions =
                subscriptionRepository.findByTenantAndSession(event.tenantId(), event.sessionId());

        // Req 6.6: no subscriptions, log "none delivered" and return without error
        if (subscriptions.isEmpty()) {
            LOG.info(() -> String.format(
                    "OrderReady [orderId=%s, session=%s, tenantId=%s]: no device subscriptions — no notification delivered.",
                    event.orderId(), shortId(event.sessionId().value()), event.tenantId()));
            return;
        }

        NotificationPayload message = payloadFor(event);
        // The deadline counts from the moment the order became READY, not from now: a replayed event is not fresher
        Instant expiresAt = event.occurredAt().plus(deadline);
        Instant now = clock.instant();

        boolean queued = false;
        for (DeviceSubscription subscription : subscriptions) {
            // Replayed event (relay retry after a crash): this device already has its push queued or sent
            if (notificationRepository.existsLive(event.tenantId(), event.orderId().value(), subscription.getId())) {
                LOG.info(() -> "OrderReady " + event.orderId() + ": push to subscription "
                        + subscription.getId() + " already queued or sent, skipping.");
                continue;
            }
            notificationRepository.save(Notification.composeForDelivery(event.tenantId(), subscription.getId(),
                    message, event.orderId().value(), event.occurredAt(), now, expiresAt));
            queued = true;
        }
        if (queued) {
            deliveryTrigger.wake();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** "Café Aurora: order #101 is ready", tapping it opens the tracking page. */
    private NotificationPayload payloadFor(OrderReady event) {
        String restaurant = restaurantRepository.findByTenantId(event.tenantId())
                .map(r -> r.getConfig().name())
                .filter(n -> !n.isBlank())
                .orElse("Your order");
        String body = orderRepository.findByTenantAndId(event.tenantId(), event.orderId())
                .map(o -> "Order #" + o.getOrderNumber() + " is ready for pickup!")
                .orElse("Your order is ready for pickup!");
        return new NotificationPayload(
                restaurant,
                body,
                "/track/" + event.sessionId().value());
    }

    /** The session id is the customer's credential for the tracking page, so logs carry only a prefix of it. */
    private static String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8) + "…";
    }
}

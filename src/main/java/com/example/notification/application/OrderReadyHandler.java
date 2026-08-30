package com.example.notification.application;

import com.example.notification.domain.Notification;
import com.example.notification.domain.NotificationPayload;
import com.example.notification.domain.NotificationRepository;
import com.example.notification.domain.NotificationSender;
import com.example.notification.domain.SendResult;
import com.example.ordering.domain.OrderReady;
import com.example.shared.domain.DomainEventPublisher;
import com.example.subscription.application.ManageSubscriptionUseCase;
import com.example.subscription.domain.DeviceSubscription;
import com.example.subscription.domain.DeviceSubscriptionRepository;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application-layer handler for the {@link OrderReady} domain event.
 *
 * <p>Orchestrates the order-ready notification flow (Req 6.1–6.8, 3.6):
 * <ol>
 *   <li>Query {@link DeviceSubscriptionRepository} for all subscriptions associated with
 *       the event's {@code sessionId} whose {@code tenantId} matches the event's
 *       {@code tenantId} (Req 6.1).</li>
 *   <li>For each subscription, compose a {@link Notification} and send it via
 *       {@link NotificationSender} (Req 6.2).</li>
 *   <li>On {@link SendResult#ACCEPTED}: mark the notification SENT, persist, publish
 *       {@code NotificationSent} (Req 6.4).</li>
 *   <li>On {@link SendResult#REJECTED}: mark FAILED, persist, record failure, and
 *       continue with the next subscription (Req 6.7, 6.8).</li>
 *   <li>On {@link SendResult#ENDPOINT_EXPIRED}: delete the subscription via
 *       {@link ManageSubscriptionUseCase#deleteExpiredSubscription}, mark FAILED,
 *       persist, and continue (Req 3.6, 6.7).</li>
 *   <li>If no subscriptions exist, record that no notification was delivered and return
 *       without error (Req 6.6).</li>
 * </ol>
 *
 * <p>Send calls are sequential for simplicity; concurrent dispatch is a later NFR
 * concern. No Spring annotations are used — wiring happens in the composition root.
 */
public class OrderReadyHandler {

    private static final Logger LOG = Logger.getLogger(OrderReadyHandler.class.getName());

    /** Notification payload used for every order-ready push (title + body). */
    private static final NotificationPayload ORDER_READY_PAYLOAD =
            new NotificationPayload("Order Ready", "Your order is ready for pickup!");

    private final DeviceSubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSender notificationSender;
    private final DomainEventPublisher eventPublisher;
    private final ManageSubscriptionUseCase subscriptionService;

    /**
     * Constructor — all dependencies are injected (no framework annotations).
     *
     * @param subscriptionRepository repository for querying device subscriptions
     * @param notificationRepository repository for persisting notification records
     * @param notificationSender     port for delivering Web Push messages
     * @param eventPublisher         port for publishing domain events
     * @param subscriptionService    use case for managing subscriptions (used to delete expired ones)
     */
    public OrderReadyHandler(DeviceSubscriptionRepository subscriptionRepository,
                             NotificationRepository notificationRepository,
                             NotificationSender notificationSender,
                             DomainEventPublisher eventPublisher,
                             ManageSubscriptionUseCase subscriptionService) {
        this.subscriptionRepository = Objects.requireNonNull(subscriptionRepository,
                "subscriptionRepository must not be null");
        this.notificationRepository = Objects.requireNonNull(notificationRepository,
                "notificationRepository must not be null");
        this.notificationSender = Objects.requireNonNull(notificationSender,
                "notificationSender must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher,
                "eventPublisher must not be null");
        this.subscriptionService = Objects.requireNonNull(subscriptionService,
                "subscriptionService must not be null");
    }

    /**
     * Handles an {@link OrderReady} event by composing and dispatching push notifications
     * to all matching device subscriptions.
     *
     * @param event the order-ready event (must not be {@code null})
     */
    public void handle(OrderReady event) {
        Objects.requireNonNull(event, "event must not be null");

        // Req 6.1 — query subscriptions for the session scoped to the event's tenant
        List<DeviceSubscription> subscriptions =
                subscriptionRepository.findByTenantAndSession(event.tenantId(), event.sessionId());

        // Req 6.6 — no subscriptions: log "none delivered" and return without error
        if (subscriptions.isEmpty()) {
            LOG.info(() -> String.format(
                    "OrderReady [orderId=%s, sessionId=%s, tenantId=%s]: no device subscriptions — no notification delivered.",
                    event.orderId(), event.sessionId(), event.tenantId()));
            return;
        }

        // Req 6.2 — for each subscription, compose and send
        for (DeviceSubscription subscription : subscriptions) {
            processSubscription(event, subscription);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void processSubscription(OrderReady event, DeviceSubscription subscription) {
        // Compose a PENDING notification for this subscription (Req 6.2)
        Notification notification = Notification.compose(
                event.tenantId(),
                subscription.getId(),
                ORDER_READY_PAYLOAD.body());
        notificationRepository.save(notification);

        SendResult result;
        try {
            result = notificationSender.send(subscription.getEndpoint(), ORDER_READY_PAYLOAD);
        } catch (Exception ex) {
            // Treat unexpected sender exceptions as a failure — Req 6.7
            LOG.log(Level.WARNING, "NotificationSender threw an unexpected exception for subscription "
                    + subscription.getId() + "; recording failure.", ex);
            notification.markFailed();
            notificationRepository.save(notification);
            return;
        }

        switch (result) {
            case ACCEPTED -> handleAccepted(notification);
            case REJECTED -> handleRejected(notification, subscription);
            case ENDPOINT_EXPIRED -> handleExpired(notification, subscription);
        }
    }

    /**
     * ACCEPTED outcome: PENDING → SENT, publish {@code NotificationSent} (Req 6.4).
     */
    private void handleAccepted(Notification notification) {
        notification.markSent(Clock.systemUTC());
        notificationRepository.save(notification);
        eventPublisher.publishAll(notification.pendingEvents());

        LOG.fine(() -> "Notification " + notification.id() + " sent successfully.");
    }

    /**
     * REJECTED outcome: record failure and continue (Req 6.7, 6.8).
     */
    private void handleRejected(Notification notification, DeviceSubscription subscription) {
        LOG.warning(() -> String.format(
                "Notification rejected for subscription %s; recording failure.",
                subscription.getId()));
        notification.markFailed();
        notificationRepository.save(notification);
    }

    /**
     * ENDPOINT_EXPIRED outcome: delete the subscription, record failure, continue (Req 3.6, 6.7).
     */
    private void handleExpired(Notification notification, DeviceSubscription subscription) {
        LOG.warning(() -> String.format(
                "Endpoint expired for subscription %s; deleting subscription.",
                subscription.getId()));
        notification.markFailed();
        notificationRepository.save(notification);
        subscriptionService.deleteExpiredSubscription(subscription.getId());
    }
}

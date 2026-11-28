package org.readybell.notification.application;

import org.readybell.notification.domain.Notification;
import org.readybell.notification.domain.NotificationRepository;
import org.readybell.notification.domain.NotificationSender;
import org.readybell.notification.domain.PushRetryPolicy;
import org.readybell.notification.domain.SendResult;
import org.readybell.shared.domain.DomainEventPublisher;
import org.readybell.subscription.application.ManageSubscriptionUseCase;
import org.readybell.subscription.domain.DeviceSubscription;
import org.readybell.subscription.domain.DeviceSubscriptionRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One delivery attempt for a notification that is due, and what follows from its outcome:
 * <ul>
 *   <li>{@link SendResult#ACCEPTED}: SENT and {@code NotificationSent};</li>
 *   <li>{@link SendResult#REJECTED}: FAILED, no retry;</li>
 *   <li>{@link SendResult#ENDPOINT_EXPIRED}: FAILED and the subscription is deleted;</li>
 *   <li>{@link SendResult#TRANSIENT_FAILURE}: stays PENDING with a later {@code next_attempt_at}, or FAILED when the
 *       retries are used up or the deadline has passed.</li>
 * </ul>
 * Nothing here sleeps: a retry is a row that becomes due later, so a push service that is slow or down holds up
 * no thread and no other order. No Spring annotations; wiring is in the composition root.
 */
public class PushDeliveryService {
    private static final Logger LOG = Logger.getLogger(PushDeliveryService.class.getName());

    private final NotificationRepository notifications;
    private final DeviceSubscriptionRepository subscriptions;
    private final NotificationSender sender;
    private final DomainEventPublisher eventPublisher;
    private final ManageSubscriptionUseCase subscriptionService;
    private final PushRetryPolicy retryPolicy;
    private final Clock clock;

    public PushDeliveryService(NotificationRepository notifications, DeviceSubscriptionRepository subscriptions,
                               NotificationSender sender, DomainEventPublisher eventPublisher,
                               ManageSubscriptionUseCase subscriptionService, PushRetryPolicy retryPolicy, Clock clock) {
        this.notifications = Objects.requireNonNull(notifications, "notifications must not be null");
        this.subscriptions = Objects.requireNonNull(subscriptions, "subscriptions must not be null");
        this.sender = Objects.requireNonNull(sender, "sender must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.subscriptionService = Objects.requireNonNull(subscriptionService, "subscriptionService must not be null");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /** Attempts the notification once and records the outcome. Does not throw. */
    public void attempt(Notification notification) {
        Instant now = clock.instant();
        if (notification.isExpired(now)) {
            giveUp(notification, "expired before it could be delivered");
            return;
        }
        Optional<DeviceSubscription> subscription = subscriptions.findById(notification.subscriptionId());
        if (subscription.isEmpty()) {
            giveUp(notification, "subscription no longer exists");
            return;
        }

        SendResult result;
        try {
            result = sender.send(subscription.get().getEndpoint(), notification.toMessage());
        } catch (Exception ex) {
            // An unexpected sender failure is treated like an unavailable push service: bounded retries
            LOG.log(Level.WARNING, "NotificationSender threw an unexpected exception for subscription "
                    + subscription.get().getId() + "; treating it as transient.", ex);
            result = SendResult.TRANSIENT_FAILURE;
        }

        switch (result) {
            case ACCEPTED -> accepted(notification);
            case REJECTED -> giveUp(notification, "rejected by the push service");
            case ENDPOINT_EXPIRED -> {
                giveUp(notification, "endpoint expired");
                subscriptionService.deleteExpiredSubscription(subscription.get().getId());
            }
            case TRANSIENT_FAILURE -> retryLater(notification);
        }
    }

    private void accepted(Notification notification) {
        notification.markSent(clock);
        notifications.save(notification);
        eventPublisher.publishAll(notification.pendingEvents());
        LOG.fine(() -> "Notification " + notification.id() + " sent successfully.");
    }

    private void retryLater(Notification notification) {
        Instant now = clock.instant();
        // attempts() counts the ones that were accepted or scheduled for retry: this one failed, so it is one more
        int failed = notification.attempts() + 1;
        Optional<Instant> next = retryPolicy.nextAttempt(failed, now, notification.expiresAt());
        if (next.isPresent()) {
            notification.scheduleRetry(next.get(), "push service unavailable");
            notifications.save(notification);
            LOG.info(() -> "Notification " + notification.id() + ": attempt " + failed + " failed, retrying at "
                    + next.get() + ".");
        } else {
            giveUp(notification, failed > 1 ? "push service unavailable, retries used up" : "push service unavailable");
        }
    }

    private void giveUp(Notification notification, String reason) {
        LOG.warning(() -> "Notification " + notification.id() + " given up: " + reason + ".");
        notification.markFailed(reason);
        notifications.save(notification);
    }
}

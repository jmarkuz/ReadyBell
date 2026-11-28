package org.readybell.notification.domain;

import org.readybell.subscription.domain.PushEndpoint;

/**
 * Outbound port for delivering a Web Push notification to a single device endpoint.
 *
 * <p>VAPID credentials and all adapter-specific details live in the implementing adapter
 * only; the domain depends exclusively on this interface.
 *
 * <p>The three possible outcomes are captured by {@link SendResult}:
 * <ul>
 *   <li>{@link SendResult#ACCEPTED} — push service accepted the message</li>
 *   <li>{@link SendResult#REJECTED} — push service refused; record failure and continue</li>
 *   <li>{@link SendResult#ENDPOINT_EXPIRED} — endpoint is gone; delete the subscription</li>
 * </ul>
 *
 * <p>Implementations must be safe to call concurrently from multiple threads (the
 * {@code OrderReadyHandler} dispatches one call per subscription on a bounded executor).
 */
public interface NotificationSender {
    /** Sends a push notification to the given {@code endpoint}. */
    SendResult send(PushEndpoint endpoint, NotificationPayload payload);
}

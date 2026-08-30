package com.example.notification.domain;

import com.example.subscription.domain.PushEndpoint;

/**
 * Outbound port for delivering a Web Push notification to a single device endpoint.
 *
 * <p>VAPID credentials and all adapter-specific details live in the implementing adapter
 * only; the domain depends exclusively on this interface (Req 6.2).
 *
 * <p>The three possible outcomes are captured by {@link SendResult}:
 * <ul>
 *   <li>{@link SendResult#ACCEPTED} — push service accepted the message (Req 6.4)</li>
 *   <li>{@link SendResult#REJECTED} — push service refused; record failure and continue
 *       (Req 6.7, 6.8)</li>
 *   <li>{@link SendResult#ENDPOINT_EXPIRED} — endpoint is gone; delete the subscription
 *       (Req 3.6)</li>
 * </ul>
 *
 * <p>Implementations must be safe to call concurrently from multiple threads (the
 * {@code OrderReadyHandler} dispatches one call per subscription on a bounded executor).
 */
public interface NotificationSender {

    /**
     * Sends a push notification to the given {@code endpoint}.
     *
     * @param endpoint the device push endpoint including keys required for encryption
     * @param payload  the notification title and body to deliver
     * @return the outcome of the send attempt — never {@code null}
     */
    SendResult send(PushEndpoint endpoint, NotificationPayload payload);
}

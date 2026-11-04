package org.readybell.notification.domain;

/**
 * Outcome returned by {@link NotificationSender} after attempting to deliver a
 * Web Push message to a device endpoint.
 *
 * <p>Each value drives a different branch in the notification flow:
 * <ul>
 *   <li>{@link #ACCEPTED} — the push service accepted the message for delivery;
 *       the {@code Notification_Service} publishes {@code NotificationSent} (Req 6.4).</li>
 *   <li>{@link #REJECTED} — the push service refused the message (e.g. payload too large,
 *       rate-limited); the failure is recorded and processing continues (Req 6.7, 6.8).</li>
 *   <li>{@link #ENDPOINT_EXPIRED} — the endpoint is no longer valid; the
 *       {@code Subscription_Service} must delete the {@code DeviceSubscription} so no
 *       further messages target it (Req 3.6).</li>
 * </ul>
 */
public enum SendResult {

    /**
     * The push service accepted the message for delivery (HTTP 201).
     * Maps to: publish {@code NotificationSent} (Req 6.4).
     */
    ACCEPTED,

    /**
     * The push service rejected the message for a recoverable or permanent reason
     * other than an expired endpoint (HTTP 4xx excluding 404/410).
     * Maps to: record delivery failure and continue (Req 6.7, 6.8).
     */
    REJECTED,

    /**
     * The device endpoint is no longer registered (HTTP 404 or 410).
     * Maps to: delete the {@code DeviceSubscription} (Req 3.6).
     */
    /** Network error, 429 or 5xx from the push service: worth retrying. */
    TRANSIENT_FAILURE,

    ENDPOINT_EXPIRED
}

package org.readybell.notification.domain;

/**
 * Lifecycle states of a {@link Notification}.
 *
 * <ul>
 *   <li>{@link #PENDING}   — composed but not yet dispatched to the push service</li>
 *   <li>{@link #SENT}      — accepted by the push service</li>
 *   <li>{@link #DELIVERED} — delivery confirmed by the push service</li>
 *   <li>{@link #FAILED}    — dispatch or delivery failed</li>
 * </ul>
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED
}

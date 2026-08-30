package com.example.notification.domain;

/**
 * Lifecycle states of a {@link Notification}.
 *
 * <ul>
 *   <li>{@link #PENDING}   — composed but not yet dispatched to the push service</li>
 *   <li>{@link #SENT}      — accepted by the push service (Req 6.4)</li>
 *   <li>{@link #DELIVERED} — delivery confirmed by the push service (Req 6.5)</li>
 *   <li>{@link #FAILED}    — dispatch or delivery failed (Req 6.7, 6.8)</li>
 * </ul>
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED
}

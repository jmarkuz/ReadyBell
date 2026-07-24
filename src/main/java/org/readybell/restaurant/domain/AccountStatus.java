package org.readybell.restaurant.domain;

/**
 * Lifecycle status of a {@link StaffAccount} (Req 8.5).
 *
 * <ul>
 *   <li>{@link #ACTIVE} — account is operational and can receive notifications.</li>
 *   <li>{@link #DEACTIVATED} — account has been disabled; it no longer receives notifications.</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    DEACTIVATED
}

package com.example.restaurant.domain;

/**
 * Role assigned to a {@link StaffAccount}.
 *
 * <ul>
 *   <li>{@link #STAFF} — regular staff member with standard notification access.</li>
 *   <li>{@link #RESTAURANT_ADMIN} — elevated role that may manage other staff accounts.</li>
 * </ul>
 */
public enum Role {
    STAFF,
    RESTAURANT_ADMIN
}

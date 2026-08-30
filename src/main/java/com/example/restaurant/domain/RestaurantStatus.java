package com.example.restaurant.domain;

/**
 * Lifecycle status of a {@link Restaurant} tenant.
 *
 * <p>A Restaurant starts {@link #ACTIVE} and may be {@link #DEACTIVATED} by a
 * Platform Admin. While deactivated, new customer sessions are rejected (Req 9.6).
 */
public enum RestaurantStatus {
    ACTIVE,
    DEACTIVATED
}

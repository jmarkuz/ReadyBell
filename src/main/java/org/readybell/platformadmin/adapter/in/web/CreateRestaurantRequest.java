package org.readybell.platformadmin.adapter.in.web;

/**
 * Request body for {@code POST /api/platform/restaurants}.
 *
 * @param identifier the unique business identifier for the new restaurant (Req 9.5)
 * @param name       the display name for the restaurant
 */
public record CreateRestaurantRequest(String identifier, String name) {}

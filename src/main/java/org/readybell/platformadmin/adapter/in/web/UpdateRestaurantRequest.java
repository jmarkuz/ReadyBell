package org.readybell.platformadmin.adapter.in.web;

/** Request body for {@code PUT /api/platform/restaurants/{tenantId}}. */
public record UpdateRestaurantRequest(String name) {}

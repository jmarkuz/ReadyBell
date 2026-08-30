package com.example.platformadmin.adapter.in.web;

/**
 * Request body for {@code PUT /api/platform/restaurants/{tenantId}}.
 *
 * @param name the new display name for the restaurant
 */
public record UpdateRestaurantRequest(String name) {}

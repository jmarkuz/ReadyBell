package org.readybell.restaurant.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /api/admin/config} (Req 8.6).
 *
 * @param name the new display name for the restaurant
 */
public record UpdateConfigRequest(@NotBlank String name) {}

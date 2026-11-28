package org.readybell.restaurant.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code PUT /api/admin/config}. */
public record UpdateConfigRequest(@NotBlank String name) {}

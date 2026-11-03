package org.readybell.restaurant.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

import org.readybell.restaurant.domain.Role;

/**
 * Request body for {@code POST /api/admin/staff} (Req 8.3, 8.4).
 *
 * @param username the desired username, unique within the tenant (Req 8.4)
 * @param role     the role string; must be a valid {@link Role}
 *                 name (e.g. {@code "STAFF"} or {@code "RESTAURANT_ADMIN"})
 */
public record CreateStaffAccountRequest(@NotBlank String username, @NotBlank String role) {}

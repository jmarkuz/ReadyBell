package org.readybell.restaurant.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/admin/staff}. */
public record CreateStaffAccountRequest(@NotBlank String username, @NotBlank String role,
                                 @NotBlank @Size(min = 10, max = 72) String password) {}

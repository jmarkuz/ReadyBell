package org.readybell.restaurant.adapter.in.web;

/**
 * Request body for {@code POST /api/admin/staff} (Req 8.3, 8.4).
 *
 * @param username the desired username, unique within the tenant (Req 8.4)
 * @param role     the role string; must be a valid {@link org.readybell.restaurant.domain.Role}
 *                 name (e.g. {@code "STAFF"} or {@code "RESTAURANT_ADMIN"})
 */
public record CreateStaffAccountRequest(String username, String role) {}

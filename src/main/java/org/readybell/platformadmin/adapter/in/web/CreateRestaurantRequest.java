package org.readybell.platformadmin.adapter.in.web;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/platform/restaurants}.
 *
 * <p>{@code adminUsername} and {@code adminPassword} are optional but must be given together; when present
 * the restaurant is created together with its first {@code RESTAURANT_ADMIN} so that someone can sign in.
 */
public record CreateRestaurantRequest(
        String identifier,
        String name,
        String adminUsername,
        @Size(min = 8, max = 72) String adminPassword) {

    public CreateRestaurantRequest(String identifier, String name) {
        this(identifier, name, null, null);
    }

    boolean hasAdmin() {
        return adminUsername != null || adminPassword != null;
    }
}

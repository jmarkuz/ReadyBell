package org.readybell.restaurant.domain;

import java.util.Objects;

/**
 * Value object holding Restaurant-level configuration (Req 8.6).
 *
 * <p>Immutable; replace the whole record to change configuration values.
 */
public record RestaurantConfig(String name) {

    public RestaurantConfig {
        Objects.requireNonNull(name, "restaurant name must not be null");
    }

    /** Creates a config with just a display name. */
    public static RestaurantConfig of(String name) {
        return new RestaurantConfig(name);
    }
}

package com.example.restaurant.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object identifying a staff account within the platform.
 *
 * <p>Wraps a non-null {@link UUID}. Use {@link #newId()} to mint a fresh
 * identity when creating a new {@link StaffAccount}.
 */
public record StaffAccountId(UUID value) {

    public StaffAccountId {
        Objects.requireNonNull(value, "staffAccountId must not be null");
    }

    /** Factory that generates a random, unique {@code StaffAccountId}. */
    public static StaffAccountId newId() {
        return new StaffAccountId(UUID.randomUUID());
    }
}

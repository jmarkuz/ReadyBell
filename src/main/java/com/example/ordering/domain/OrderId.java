package com.example.ordering.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity value object for an {@link Order}.
 *
 * <p>Wraps a non-null {@link UUID}. Every order gets a distinct {@code OrderId}
 * assigned at creation time.
 */
public record OrderId(UUID value) {

    public OrderId {
        Objects.requireNonNull(value, "orderId must not be null");
    }

    /** Creates a new, random {@code OrderId}. */
    public static OrderId newId() {
        return new OrderId(UUID.randomUUID());
    }
}

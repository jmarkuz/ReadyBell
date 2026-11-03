package org.readybell.ordering.domain;

import java.util.Objects;

/** One line of an order: what was ordered and how many. */
public record OrderItem(String name, int quantity) {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_QUANTITY = 99;

    public OrderItem {
        Objects.requireNonNull(name, "name must not be null");
        name = name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("item name must be 1-" + MAX_NAME_LENGTH + " characters");
        }
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("item quantity must be 1-" + MAX_QUANTITY);
        }
    }
}

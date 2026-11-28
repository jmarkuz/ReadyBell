package org.readybell.ordering.domain;

import java.time.Instant;
import java.util.List;

/** Optional descriptive data supplied by staff when an order is created. */
public record OrderDetails(String customerName, List<OrderItem> items, Instant estimatedReadyAt) {
    public static final int MAX_ITEMS = 50;
    public static final int MAX_CUSTOMER_NAME = 100;

    public static final OrderDetails NONE = new OrderDetails(null, List.of(), null);

    public OrderDetails {
        items = items == null ? List.of() : List.copyOf(items);
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("an order may contain at most " + MAX_ITEMS + " items");
        }
        if (customerName != null) {
            customerName = customerName.strip();
            if (customerName.isEmpty()) {
                customerName = null;
            } else if (customerName.length() > MAX_CUSTOMER_NAME) {
                throw new IllegalArgumentException("customerName must be at most " + MAX_CUSTOMER_NAME + " characters");
            }
        }
    }
}

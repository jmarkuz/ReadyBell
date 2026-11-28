package org.readybell.ordering.domain;

import java.time.Instant;
import java.util.Set;

/** Staff-panel list query: filters, sorting and 1-based pagination. */
public record OrderQuery(int page, int pageSize, Set<OrderStatus> statuses, Sort sortBy, boolean ascending, String search,
                         Instant from, Instant to) {
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;

    public enum Sort { CREATED, ETA, STATUS, CUSTOMER_NAME, ORDER_ID }

    public OrderQuery {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        sortBy = sortBy == null ? Sort.CREATED : sortBy;
        search = search == null || search.isBlank() ? null : search.strip();
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
    }
}

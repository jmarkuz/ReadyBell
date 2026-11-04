package org.readybell.ordering.domain;

import java.util.EnumMap;
import java.util.Map;

/** Number of a tenant's orders per status; every status is present, zero when it has no orders. */
public record OrderStatusCounts(Map<OrderStatus, Long> counts) {

    public OrderStatusCounts {
        var full = new EnumMap<OrderStatus, Long>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            full.put(status, counts.getOrDefault(status, 0L));
        }
        counts = Map.copyOf(full);
    }

    public long count(OrderStatus status) {
        return counts.get(status);
    }

    public long total() {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }
}

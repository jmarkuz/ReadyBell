package org.readybell.ordering.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    CREATED, IN_PROGRESS, READY, COMPLETED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            CREATED,     EnumSet.of(IN_PROGRESS, CANCELLED),
            IN_PROGRESS, EnumSet.of(READY, CANCELLED),
            READY,       EnumSet.of(COMPLETED, CANCELLED),
            COMPLETED,   EnumSet.noneOf(OrderStatus.class),
            CANCELLED,   EnumSet.noneOf(OrderStatus.class));

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}

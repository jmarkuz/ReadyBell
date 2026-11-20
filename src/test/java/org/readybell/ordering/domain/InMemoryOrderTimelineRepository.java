package org.readybell.ordering.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Fake of {@link OrderTimelineRepository} that remembers what was recorded. */
public class InMemoryOrderTimelineRepository implements OrderTimelineRepository {

    public record Transition(OrderId orderId, OrderStatus from, OrderStatus to, Instant at) {}

    public final List<Transition> transitions = new ArrayList<>();
    public final List<OrderId> finished = new ArrayList<>();

    @Override
    public void recordTransition(Order order, OrderStatus from, Instant at) {
        transitions.add(new Transition(order.getId(), from, order.getStatus(), at));
    }

    @Override
    public void recordFinished(Order order, Instant at) {
        finished.add(order.getId());
    }
}

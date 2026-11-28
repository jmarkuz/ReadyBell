package org.readybell.ordering.domain;

/**
 * Thrown when a requested {@link OrderStatus} transition is not permitted by the
 * {@link Order} lifecycle rules.
 *
 * <p>Covers two distinct cases:
 * <ul>
 *   <li><em>Disallowed transition</em> — the source-state/target-state pair is not in the
 *       allowed set.</li>
 *   <li><em>Self-transition</em> — the target state equals the current state.</li>
 * </ul>
 *
 * <p>The {@link Order} remains in its current state when this exception is thrown.
 */
public class InvalidTransitionException extends RuntimeException {
    private final OrderStatus from;
    private final OrderStatus to;

    public InvalidTransitionException(OrderStatus from, OrderStatus to) {
        super("Invalid order status transition: " + from + " -> " + to);
        this.from = from;
        this.to   = to;
    }

    public OrderStatus getFrom() {
        return from;
    }

    public OrderStatus getTo() {
        return to;
    }
}

package org.readybell.ordering.domain;

/**
 * Thrown when a requested {@link OrderStatus} transition is not permitted by the
 * {@link Order} lifecycle rules.
 *
 * <p>Covers two distinct cases:
 * <ul>
 *   <li><em>Disallowed transition</em> — the source-state/target-state pair is not in the
 *       allowed set (Req 5.3).</li>
 *   <li><em>Self-transition</em> — the target state equals the current state (Req 5.4).</li>
 * </ul>
 *
 * <p>The {@link Order} remains in its current state when this exception is thrown.
 */
public class InvalidTransitionException extends RuntimeException {

    private final OrderStatus from;
    private final OrderStatus to;

    /**
     * @param from the current (source) order status
     * @param to   the requested (target) order status
     */
    public InvalidTransitionException(OrderStatus from, OrderStatus to) {
        super("Invalid order status transition: " + from + " -> " + to);
        this.from = from;
        this.to   = to;
    }

    /** The status the order was in when the invalid transition was attempted. */
    public OrderStatus getFrom() {
        return from;
    }

    /** The status that was (invalidly) requested. */
    public OrderStatus getTo() {
        return to;
    }
}

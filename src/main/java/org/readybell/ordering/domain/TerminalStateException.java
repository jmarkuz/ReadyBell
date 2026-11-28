package org.readybell.ordering.domain;

/**
 * Thrown when a state transition is attempted on an {@link Order} that has already
 * reached a terminal state ({@link OrderStatus#COMPLETED} or {@link OrderStatus#CANCELLED}).
 *
 * <p>Once an order is terminal, no further transitions are permitted.
 * The {@link Order} remains in its current state when this exception is thrown.
 */
public class TerminalStateException extends RuntimeException {
    private final OrderStatus terminalStatus;

    public TerminalStateException(OrderStatus terminalStatus) {
        super("Order is in terminal state " + terminalStatus + " and cannot be transitioned further");
        this.terminalStatus = terminalStatus;
    }

    public OrderStatus getTerminalStatus() {
        return terminalStatus;
    }
}

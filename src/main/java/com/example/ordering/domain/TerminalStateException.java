package com.example.ordering.domain;

/**
 * Thrown when a state transition is attempted on an {@link Order} that has already
 * reached a terminal state ({@link OrderStatus#COMPLETED} or {@link OrderStatus#CANCELLED}).
 *
 * <p>Once an order is terminal, no further transitions are permitted (Req 5.7).
 * The {@link Order} remains in its current state when this exception is thrown.
 */
public class TerminalStateException extends RuntimeException {

    private final OrderStatus terminalStatus;

    /**
     * @param terminalStatus the terminal status the order is already in
     */
    public TerminalStateException(OrderStatus terminalStatus) {
        super("Order is in terminal state " + terminalStatus + " and cannot be transitioned further");
        this.terminalStatus = terminalStatus;
    }

    /** The terminal status that prevented the transition. */
    public OrderStatus getTerminalStatus() {
        return terminalStatus;
    }
}

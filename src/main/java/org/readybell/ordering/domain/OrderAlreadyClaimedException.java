package org.readybell.ordering.domain;

/** The order was already claimed at the counter; each order can be claimed once. */
public class OrderAlreadyClaimedException extends RuntimeException {

    public OrderAlreadyClaimedException(OrderId orderId) {
        super("Order " + orderId.value() + " is already claimed");
    }
}

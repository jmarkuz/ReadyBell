package org.readybell.subscription.application;

/** A session already has the maximum number of push subscriptions (a customer has a few devices, not hundreds). */
public class TooManySubscriptionsException extends RuntimeException {

    public TooManySubscriptionsException(int max) {
        super("A session can have at most " + max + " push subscriptions");
    }
}

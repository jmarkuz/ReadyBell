package org.readybell.subscription.domain;

/** A push subscription whose endpoint or keys are not acceptable; answered as a 400 (it extends {@link IllegalArgumentException}). */
public class InvalidPushEndpointException extends IllegalArgumentException {

    public InvalidPushEndpointException(String message) {
        super(message);
    }
}

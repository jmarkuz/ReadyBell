package org.readybell.ordering.application;

import org.readybell.session.domain.SessionStatus;
import org.readybell.session.domain.CustomerSessionId;

/**
 * Thrown when an order-creation request references a customer session that does not
 * exist or is not in the {@link SessionStatus#ACTIVE} state.
 *
 * <p>Requirements 4.5: if a Restaurant_Staff member requests creation of an Order for a
 * Customer_Session that does not exist or is not active, the Order_Service SHALL reject
 * the request without creating an Order and return an error response identifying the
 * Customer_Session as invalid.
 */
public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException(CustomerSessionId sessionId) {
        super("Customer session is invalid or not active: " + sessionId.value());
    }

    public InvalidSessionException(String message) {
        super(message);
    }
}

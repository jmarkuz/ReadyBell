package com.example.subscription.application;

import com.example.session.domain.CustomerSessionId;

/**
 * Thrown when a subscription registration request references a customer session that
 * does not exist, is not active, or does not belong to the requested tenant.
 *
 * <p>Requirement 3.1: the Subscription_Service SHALL register a Device_Subscription
 * associated with the Customer_Session — if the session is absent or inactive the
 * registration must be rejected.
 */
public class SessionNotActiveException extends RuntimeException {

    public SessionNotActiveException(CustomerSessionId sessionId) {
        super("Customer session is not active: " + sessionId.value());
    }

    public SessionNotActiveException(String message) {
        super(message);
    }
}

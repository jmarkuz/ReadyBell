package org.readybell.platform.auth;

import java.time.Duration;
import java.util.Optional;

/**
 * Short-lived, single-use tickets that let a browser {@code EventSource} (which cannot set headers)
 * open the staff SSE stream without putting the JWT in the URL. A ticket maps to the JWT of the
 * user who requested it, so user, role and tenant are exactly those of the requester.
 */
public interface StreamTicketStore {

    /** Stores {@code jwt} under a new random ticket valid for {@code ttl}; returns the ticket. */
    String issue(String jwt, Duration ttl);

    /** Returns the JWT behind {@code ticket} and invalidates the ticket; empty if unknown, used or expired. */
    Optional<String> consume(String ticket);
}

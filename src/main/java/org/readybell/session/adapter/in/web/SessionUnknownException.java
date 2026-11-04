package org.readybell.session.adapter.in.web;

import java.util.UUID;

/** Raised by the public tracking endpoint for a session id that does not exist. */
public class SessionUnknownException extends RuntimeException {
    public SessionUnknownException(UUID id) {
        super("Unknown session " + id);
    }
}

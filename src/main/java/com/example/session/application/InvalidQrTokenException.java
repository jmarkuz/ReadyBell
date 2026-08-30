package com.example.session.application;

/**
 * Thrown when a QR token is malformed, blank, or does not resolve to a
 * known Restaurant.
 *
 * <p>Requirements 1.4 and 1.7 both mandate that the Session_Service reject
 * invalid QR tokens and return an error identifying the QR token as invalid.
 * A single unchecked exception covers both failure modes so callers can handle
 * them uniformly.
 *
 * <p>Req 1.4: QR code is well-formed but does not resolve to an existing Restaurant.
 * <p>Req 1.7: QR code is malformed or cannot be parsed to identify a Restaurant.
 */
public class InvalidQrTokenException extends RuntimeException {

    public InvalidQrTokenException(String message) {
        super(message);
    }
}

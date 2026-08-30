package com.example.session.application;

/**
 * Thrown when a QR code token is malformed, blank, or does not resolve to a
 * known Restaurant.
 *
 * <p>Requirements 1.4 and 1.7 both mandate that the Session_Service reject
 * invalid QR codes and return an error identifying the QR code as invalid.
 * A single unchecked exception covers both failure modes so callers can handle
 * them uniformly.
 */
public class InvalidQrCodeException extends RuntimeException {

    public InvalidQrCodeException(String message) {
        super(message);
    }
}

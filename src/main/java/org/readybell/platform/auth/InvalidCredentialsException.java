package org.readybell.platform.auth;

/** Login failed or a token could not be refreshed; mapped to 401 problem+json. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}

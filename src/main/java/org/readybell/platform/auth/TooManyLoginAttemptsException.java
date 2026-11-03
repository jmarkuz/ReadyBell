package org.readybell.platform.auth;

import java.time.Duration;

/** Too many failed logins; mapped to 429 with a {@code Retry-After} header. */
public class TooManyLoginAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyLoginAttemptsException(Duration retryAfter) {
        super("Too many failed login attempts. Try again later.");
        this.retryAfter = retryAfter;
    }

    public long retryAfterSeconds() {
        return Math.max(1, retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0));
    }
}

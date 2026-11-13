package org.readybell.ordering.adapter.in.web;

/** The counter claim cannot be served; the reason is deliberately not disclosed (see CounterClaimUseCase). */
public class ClaimUnavailableException extends RuntimeException {

    public ClaimUnavailableException() {
        super("Nothing can be claimed with these details");
    }
}

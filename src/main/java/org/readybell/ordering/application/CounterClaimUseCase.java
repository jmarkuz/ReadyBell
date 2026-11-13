package org.readybell.ordering.application;

import org.readybell.session.domain.CustomerSessionId;

import java.util.Optional;

/**
 * Counter QR flow: the restaurant shows one QR code at the pickup area; a customer opens it, types the number from the
 * receipt and receives the tracking session of that order.
 *
 * <p>Every failure (unknown restaurant, no such number, already claimed, finished, too old) is the same empty result,
 * so the endpoint cannot be used to find out which orders exist.
 */
public interface CounterClaimUseCase {

    /** The public name of an active restaurant, for the page the counter QR opens. */
    Optional<String> restaurantName(String restaurantIdentifier);

    /**
     * Claims the order with the given number and returns its session, or empty when it cannot be claimed.
     * A claimed order cannot be claimed again.
     */
    Optional<CustomerSessionId> claim(String restaurantIdentifier, int orderNumber);
}

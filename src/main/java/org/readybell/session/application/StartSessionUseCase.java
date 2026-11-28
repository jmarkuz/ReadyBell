package org.readybell.session.application;

import org.readybell.restaurant.domain.DeactivatedRestaurantException;
import org.readybell.session.domain.CustomerSession;

/**
 * Inbound port (use-case interface) for starting a customer session from a QR code.
 *
 * <p>This interface is the boundary between inbound adapters (REST controllers, tests)
 * and the application service that orchestrates the domain. Nothing outside the
 * application layer depends on the concrete implementation; wiring happens in the
 * composition root.
 */
public interface StartSessionUseCase {
    /**
     * Starts a new customer session for the restaurant identified by {@code qrToken}.
     *
     * <p>The token is the restaurant's unique business identifier (or a simple
     * parseable format encoding it). The service:
     * <ol>
     *   <li>Validates the token is non-null and non-blank.</li>
     *   <li>Resolves the Restaurant by its identifier.</li>
     *   <li>Asserts the Restaurant is active.</li>
     *   <li>Creates and persists a new {@link CustomerSession}.</li>
     *   <li>Publishes all pending domain events.</li>
     * </ol>
     *
     * @throws InvalidQrTokenException         if {@code qrToken} is blank or does not
     *                                         resolve to a known restaurant
     * @throws DeactivatedRestaurantException  if the resolved restaurant is deactivated
     */
    CustomerSession startSession(String qrToken);
}

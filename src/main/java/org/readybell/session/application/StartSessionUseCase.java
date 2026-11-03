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
 *
 * <p>Requirements covered: 1.1, 1.4, 1.6, 1.7, 9.6.
 */
public interface StartSessionUseCase {

    /**
     * Starts a new customer session for the restaurant identified by {@code qrToken}.
     *
     * <p>The token is the restaurant's unique business identifier (or a simple
     * parseable format encoding it). The service:
     * <ol>
     *   <li>Validates the token is non-null and non-blank (Req 1.7).</li>
     *   <li>Resolves the Restaurant by its identifier (Req 1.4).</li>
     *   <li>Asserts the Restaurant is active (Req 9.6).</li>
     *   <li>Creates and persists a new {@link CustomerSession} (Req 1.1, 1.6).</li>
     *   <li>Publishes all pending domain events (Req 1.2).</li>
     * </ol>
     *
     * @param qrToken the QR-code payload (restaurant identifier or parseable token)
     * @return the newly created, ACTIVE {@link CustomerSession}
     * @throws InvalidQrTokenException         if {@code qrToken} is blank or does not
     *                                         resolve to a known restaurant (Req 1.4, 1.7)
     * @throws DeactivatedRestaurantException  if the resolved restaurant is deactivated
     *                                         (Req 9.6)
     */
    CustomerSession startSession(String qrToken);
}

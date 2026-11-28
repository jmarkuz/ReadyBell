package org.readybell.session.application;

import org.readybell.restaurant.domain.DeactivatedRestaurantException;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.DomainEventPublisher;
import org.readybell.shared.domain.TenantId;

import java.time.Clock;
import java.util.Objects;

/**
 * Application service implementing {@link StartSessionUseCase}.
 *
 * <p>This class is plain Java — no Spring annotations. Constructor injection provides
 * every dependency; wiring happens in the composition root (Spring {@code @Configuration}
 * or equivalent). The domain and adapter layers are referenced only through their
 * port interfaces, preserving the Clean Architecture dependency rule.
 */
public class SessionService implements StartSessionUseCase {
    private final RestaurantRepository restaurantRepository;
    private final CustomerSessionRepository sessionRepository;
    private final DomainEventPublisher eventPublisher;

    public SessionService(RestaurantRepository restaurantRepository,
                          CustomerSessionRepository sessionRepository,
                          DomainEventPublisher eventPublisher) {
        this.restaurantRepository = Objects.requireNonNull(restaurantRepository,
                "restaurantRepository must not be null");
        this.sessionRepository    = Objects.requireNonNull(sessionRepository,
                "sessionRepository must not be null");
        this.eventPublisher       = Objects.requireNonNull(eventPublisher,
                "eventPublisher must not be null");
    }

    @Override
    public CustomerSession startSession(String qrToken) {
        if (qrToken == null || qrToken.isBlank()) {
            throw new InvalidQrTokenException("QR token must not be null or blank");
        }

        var restaurant = restaurantRepository.findByIdentifier(qrToken)
                .orElseThrow(() -> new InvalidQrTokenException(
                        "QR token does not resolve to a known restaurant: " + qrToken));

        // throws DeactivatedRestaurantException if status is DEACTIVATED
        restaurant.checkCanStartSession();

        // create the session, stamping it with the restaurant's TenantId
        var session = CustomerSession.start(restaurant.getTenantId(), Clock.systemUTC());

        sessionRepository.save(session);

        eventPublisher.publishAll(session.getPendingEvents());

        // clear pending events now that they have been published
        session.clearPendingEvents();

        return session;
    }
}

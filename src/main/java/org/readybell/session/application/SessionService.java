package org.readybell.session.application;

import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.session.domain.CustomerSession;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.DomainEventPublisher;

import java.time.Clock;
import java.util.Objects;

/**
 * Application service implementing {@link StartSessionUseCase}.
 *
 * <p>This class is plain Java — no Spring annotations. Constructor injection provides
 * every dependency; wiring happens in the composition root (Spring {@code @Configuration}
 * or equivalent). The domain and adapter layers are referenced only through their
 * port interfaces, preserving the Clean Architecture dependency rule.
 *
 * <p>Requirements covered: 1.1, 1.4, 1.6, 1.7, 9.6.
 */
public class SessionService implements StartSessionUseCase {

    private final RestaurantRepository restaurantRepository;
    private final CustomerSessionRepository sessionRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * @param restaurantRepository repository port for resolving the Restaurant from the
     *                             QR token identifier (Req 1.4, 9.6)
     * @param sessionRepository    repository port for persisting the new session (Req 1.1, 1.6)
     * @param eventPublisher       outbound port for publishing domain events (Req 1.2, 11.1)
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>Implementation steps:
     * <ol>
     *   <li>Validate {@code qrToken}: reject if null or blank →
     *       {@link InvalidQrTokenException} (Req 1.7).</li>
     *   <li>Resolve the Restaurant via
     *       {@link RestaurantRepository#findByIdentifier(String)}: if absent →
     *       {@link InvalidQrTokenException} (Req 1.4).</li>
     *   <li>Call {@link org.readybell.restaurant.domain.Restaurant#checkCanStartSession()}:
     *       throws {@link org.readybell.restaurant.domain.DeactivatedRestaurantException}
     *       if DEACTIVATED (Req 9.6).</li>
     *   <li>Create the session via
     *       {@link CustomerSession#start(org.readybell.shared.domain.TenantId, Clock)}
     *       using {@link Clock#systemUTC()} (Req 1.1, 1.6).</li>
     *   <li>Persist via {@link CustomerSessionRepository#save(CustomerSession)}.</li>
     *   <li>Publish pending events via
     *       {@link DomainEventPublisher#publishAll(java.util.List)} (Req 1.2).</li>
     *   <li>Clear pending events on the aggregate.</li>
     *   <li>Return the session.</li>
     * </ol>
     */
    @Override
    public CustomerSession startSession(String qrToken) {
        // Step 1 — validate the token (Req 1.7)
        if (qrToken == null || qrToken.isBlank()) {
            throw new InvalidQrTokenException("QR token must not be null or blank");
        }

        // Step 2 — resolve the Restaurant by identifier (Req 1.4)
        var restaurant = restaurantRepository.findByIdentifier(qrToken)
                .orElseThrow(() -> new InvalidQrTokenException(
                        "QR token does not resolve to a known restaurant: " + qrToken));

        // Step 3 — guard against deactivated restaurants (Req 9.6)
        // throws DeactivatedRestaurantException if status is DEACTIVATED
        restaurant.checkCanStartSession();

        // Step 4 — create the session, stamping it with the restaurant's TenantId (Req 1.1, 1.6)
        var session = CustomerSession.start(restaurant.getTenantId(), Clock.systemUTC());

        // Step 5 — persist the session
        sessionRepository.save(session);

        // Step 6 — publish all pending domain events through the port (Req 1.2, 11.1)
        eventPublisher.publishAll(session.getPendingEvents());

        // Step 7 — clear pending events now that they have been published
        session.clearPendingEvents();

        // Step 8 — return the newly created session
        return session;
    }
}

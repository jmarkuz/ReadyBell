package org.readybell.session.application;

import org.readybell.restaurant.domain.DeactivatedRestaurantException;
import org.readybell.restaurant.domain.Restaurant;
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
 *
 * <p>Requirements covered: 1.1, 1.4, 1.6, 1.7, 9.6.
 */
public class StartSessionService implements StartSessionUseCase {

    private final RestaurantRepository restaurantRepository;
    private final CustomerSessionRepository sessionRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * @param restaurantRepository repository port for resolving the Restaurant from the
     *                             QR token identifier
     * @param sessionRepository    repository port for persisting the new session
     * @param eventPublisher       outbound port for publishing domain events
     * @param clock                clock used to timestamp the session; injectable for
     *                             deterministic testing
     */
    public StartSessionService(RestaurantRepository restaurantRepository,
                               CustomerSessionRepository sessionRepository,
                               DomainEventPublisher eventPublisher,
                               Clock clock) {
        this.restaurantRepository = Objects.requireNonNull(restaurantRepository,
                "restaurantRepository must not be null");
        this.sessionRepository    = Objects.requireNonNull(sessionRepository,
                "sessionRepository must not be null");
        this.eventPublisher       = Objects.requireNonNull(eventPublisher,
                "eventPublisher must not be null");
        this.clock                = Objects.requireNonNull(clock,
                "clock must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation steps:
     * <ol>
     *   <li>Parse {@code qrToken}: reject if null or blank → {@link InvalidQrCodeException}
     *       (Req 1.7). For now the token IS the restaurant identifier (or uses the simple
     *       "restaurant:{identifier}" prefix format).</li>
     *   <li>Resolve the Restaurant via
     *       {@link RestaurantRepository#findByIdentifier(String)}: if absent →
     *       {@link InvalidQrCodeException} (Req 1.4).</li>
     *   <li>Call {@link Restaurant#checkCanStartSession()}:
     *       throws {@link DeactivatedRestaurantException}
     *       if DEACTIVATED (Req 9.6).</li>
     *   <li>Create the session via
     *       {@link CustomerSession#start(TenantId, Clock)}
     *       (Req 1.1, 1.6).</li>
     *   <li>Persist via {@link CustomerSessionRepository#save(CustomerSession)}.</li>
     *   <li>Publish pending events via
     *       {@link DomainEventPublisher#publishAll(java.util.List)} (Req 1.2).</li>
     *   <li>Clear pending events on the aggregate.</li>
     *   <li>Return the session.</li>
     * </ol>
     */
    @Override
    public CustomerSession startSession(String qrToken) {
        // Step 1 — parse / validate the token
        String identifier = parseIdentifier(qrToken);

        // Step 2 — resolve the Restaurant
        var restaurant = restaurantRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new InvalidQrCodeException(
                        "QR code does not resolve to a known restaurant"));

        // Step 3 — guard against deactivated restaurants (throws DeactivatedRestaurantException)
        restaurant.checkCanStartSession();

        // Step 4 — create the session
        var session = CustomerSession.start(restaurant.getTenantId(), clock);

        // Step 5 — persist
        sessionRepository.save(session);

        // Step 6 — publish pending domain events
        eventPublisher.publishAll(session.getPendingEvents());

        // Step 7 — clear pending events
        session.clearPendingEvents();

        // Step 8 — return
        return session;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Parses the QR token into a restaurant identifier.
     *
     * <p>The token format is intentionally simple:
     * <ul>
     *   <li>Plain identifier — the token value is used directly.</li>
     *   <li>Prefixed format {@code "restaurant:<identifier>"} — the prefix is stripped.</li>
     * </ul>
     *
     * @param qrToken the raw token value
     * @return the restaurant identifier (never null or blank)
     * @throws InvalidQrCodeException if the token is null, blank, or produces a blank
     *                                identifier after prefix stripping
     */
    private String parseIdentifier(String qrToken) {
        if (qrToken == null || qrToken.isBlank()) {
            throw new InvalidQrCodeException("QR code token must not be null or blank");
        }

        String identifier;
        if (qrToken.startsWith("restaurant:")) {
            identifier = qrToken.substring("restaurant:".length());
        } else {
            identifier = qrToken;
        }

        if (identifier.isBlank()) {
            throw new InvalidQrCodeException("QR code token must not be null or blank");
        }

        return identifier;
    }
}

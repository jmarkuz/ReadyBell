package org.readybell.session.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.restaurant.domain.DeactivatedRestaurantException;
import org.readybell.restaurant.domain.InMemoryRestaurantRepository;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.session.domain.InMemoryCustomerSessionRepository;
import org.readybell.shared.domain.RecordingDomainEventPublisher;
import org.readybell.shared.domain.TenantId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionServiceTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());

    private InMemoryRestaurantRepository restaurants;
    private InMemoryCustomerSessionRepository sessions;
    private RecordingDomainEventPublisher events;
    private SessionService service;

    @BeforeEach
    void setUp() {
        restaurants = new InMemoryRestaurantRepository();
        sessions = new InMemoryCustomerSessionRepository();
        events = new RecordingDomainEventPublisher();
        service = new SessionService(restaurants, sessions, events);
        restaurants.save(Restaurant.create(TENANT, "cafe-aurora", RestaurantConfig.of("Café Aurora")));
    }

    @Test
    void startsAnActiveSessionForTheRestaurantOfTheQrToken() {
        var session = service.startSession("cafe-aurora");

        assertThat(session.isActive()).isTrue();
        assertThat(session.getTenantId()).isEqualTo(TENANT);
        assertThat(sessions.findByTenantAndId(TENANT, session.getId())).isPresent();
        assertThat(events.publishedCount()).isEqualTo(1);                      // SessionStarted
        assertThat(session.getPendingEvents()).isEmpty();
    }

    @Test
    void blankOrMissingTokenIsRejected() {
        assertThatThrownBy(() -> service.startSession(null)).isInstanceOf(InvalidQrTokenException.class);
        assertThatThrownBy(() -> service.startSession("")).isInstanceOf(InvalidQrTokenException.class);
        assertThatThrownBy(() -> service.startSession("   ")).isInstanceOf(InvalidQrTokenException.class);
        assertThat(sessions.size()).isZero();
    }

    @Test
    void unknownRestaurantIsRejected() {
        assertThatThrownBy(() -> service.startSession("no-such-cafe")).isInstanceOf(InvalidQrTokenException.class);
        assertThat(sessions.size()).isZero();
        assertThat(events.hasNoPublishedEvents()).isTrue();
    }

    @Test
    void deactivatedRestaurantCannotStartSessions() {
        var restaurant = restaurants.findByIdentifier("cafe-aurora").orElseThrow();
        restaurant.deactivate();
        restaurants.save(restaurant);

        assertThatThrownBy(() -> service.startSession("cafe-aurora")).isInstanceOf(DeactivatedRestaurantException.class);
        assertThat(sessions.size()).isZero();
    }

    @Test
    void constructorRejectsNulls() {
        assertThatThrownBy(() -> new SessionService(null, sessions, events)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessionService(restaurants, null, events)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessionService(restaurants, sessions, null)).isInstanceOf(NullPointerException.class);
    }
}

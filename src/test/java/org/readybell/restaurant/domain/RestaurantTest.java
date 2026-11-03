package org.readybell.restaurant.domain;

import org.junit.jupiter.api.Test;
import org.readybell.shared.domain.TenantId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RestaurantTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final String IDENTIFIER = "acme-restaurant";
    private static final RestaurantConfig CONFIG = RestaurantConfig.of("Acme Restaurant");

    // --- factory ---

    @Test
    void create_returnsActiveRestaurant() {
        Restaurant r = Restaurant.create(TENANT, IDENTIFIER, CONFIG);

        assertEquals(TENANT, r.getTenantId());
        assertEquals(IDENTIFIER, r.getIdentifier());
        assertEquals(RestaurantStatus.ACTIVE, r.getStatus());
        assertTrue(r.isActive());
        assertEquals(CONFIG, r.getConfig());
    }

    @Test
    void create_throwsOnNullTenantId() {
        assertThrows(NullPointerException.class,
                () -> Restaurant.create(null, IDENTIFIER, CONFIG));
    }

    @Test
    void create_throwsOnNullIdentifier() {
        assertThrows(NullPointerException.class,
                () -> Restaurant.create(TENANT, null, CONFIG));
    }

    @Test
    void create_throwsOnBlankIdentifier() {
        assertThrows(IllegalArgumentException.class,
                () -> Restaurant.create(TENANT, "   ", CONFIG));
    }

    @Test
    void create_throwsOnNullConfig() {
        assertThrows(NullPointerException.class,
                () -> Restaurant.create(TENANT, IDENTIFIER, null));
    }

    // --- deactivate ---

    @Test
    void deactivate_setsStatusToDeactivated() {
        Restaurant r = Restaurant.create(TENANT, IDENTIFIER, CONFIG);
        r.deactivate();

        assertEquals(RestaurantStatus.DEACTIVATED, r.getStatus());
        assertFalse(r.isActive());
    }

    @Test
    void deactivate_isIdempotent() {
        Restaurant r = Restaurant.create(TENANT, IDENTIFIER, CONFIG);
        r.deactivate();
        r.deactivate(); // must not throw

        assertEquals(RestaurantStatus.DEACTIVATED, r.getStatus());
    }

    // --- checkCanStartSession ---

    @Test
    void checkCanStartSession_doesNotThrowWhenActive() {
        Restaurant r = Restaurant.create(TENANT, IDENTIFIER, CONFIG);

        assertDoesNotThrow(r::checkCanStartSession);
    }

    @Test
    void checkCanStartSession_throwsDeactivatedRestaurantExceptionWhenDeactivated() {
        Restaurant r = Restaurant.create(TENANT, IDENTIFIER, CONFIG);
        r.deactivate();

        DeactivatedRestaurantException ex = assertThrows(
                DeactivatedRestaurantException.class, r::checkCanStartSession);
        assertEquals(IDENTIFIER, ex.getRestaurantIdentifier());
    }

    // --- updateConfig ---

    @Test
    void updateConfig_replacesConfig() {
        Restaurant r = Restaurant.create(TENANT, IDENTIFIER, CONFIG);
        RestaurantConfig updated = RestaurantConfig.of("New Name");

        r.updateConfig(updated);

        assertEquals(updated, r.getConfig());
    }

    @Test
    void updateConfig_throwsOnNull() {
        Restaurant r = Restaurant.create(TENANT, IDENTIFIER, CONFIG);

        assertThrows(NullPointerException.class, () -> r.updateConfig(null));
    }
}

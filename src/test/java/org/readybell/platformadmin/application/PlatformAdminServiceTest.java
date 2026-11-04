package org.readybell.platformadmin.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.restaurant.application.DuplicateUsernameException;
import org.readybell.restaurant.application.RestaurantNotFoundException;
import org.readybell.restaurant.domain.InMemoryRestaurantRepository;
import org.readybell.restaurant.domain.InMemoryStaffAccountRepository;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.RestaurantStatus;
import org.readybell.restaurant.domain.Role;
import org.readybell.restaurant.domain.StaffAccount;
import org.readybell.shared.domain.TenantId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformAdminServiceTest {

    private InMemoryRestaurantRepository restaurants;
    private InMemoryStaffAccountRepository staff;
    private PlatformAdminService service;

    @BeforeEach
    void setUp() {
        restaurants = new InMemoryRestaurantRepository();
        staff = new InMemoryStaffAccountRepository();
        service = new PlatformAdminService(restaurants, staff);
    }

    // ---- createRestaurant -------------------------------------------------------------------

    @Test
    void createsARestaurantWithAGeneratedTenant() {
        Restaurant restaurant = service.createRestaurant("cafe", RestaurantConfig.of("Café"));

        assertThat(restaurant.getTenantId()).isNotNull();
        assertThat(restaurant.getIdentifier()).isEqualTo("cafe");
        assertThat(restaurants.findByIdentifier("cafe")).isPresent();
    }

    @Test
    void twoRestaurantsGetDifferentTenants() {
        var a = service.createRestaurant("a", RestaurantConfig.of("A"));
        var b = service.createRestaurant("b", RestaurantConfig.of("B"));

        assertThat(a.getTenantId()).isNotEqualTo(b.getTenantId());
    }

    @Test
    void blankIdentifierIsRejected() {
        assertThatThrownBy(() -> service.createRestaurant(null, RestaurantConfig.of("x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createRestaurant("  ", RestaurantConfig.of("x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(restaurants.size()).isZero();
    }

    @Test
    void duplicateIdentifierIsRejectedAndNothingIsWritten() {
        service.createRestaurant("cafe", RestaurantConfig.of("Café"));

        assertThatThrownBy(() -> service.createRestaurant("cafe", RestaurantConfig.of("Other")))
                .isInstanceOf(DuplicateRestaurantIdentifierException.class);
        assertThat(restaurants.size()).isEqualTo(1);
    }

    @Test
    void createsARestaurantTogetherWithItsFirstAdmin() {
        Restaurant restaurant = service.createRestaurant("cafe", RestaurantConfig.of("Café"), " Owner ", "hash");

        StaffAccount admin = staff.findByUsername("owner").orElseThrow();
        assertThat(admin.getTenantId()).isEqualTo(restaurant.getTenantId());
        assertThat(admin.getRole()).isEqualTo(Role.RESTAURANT_ADMIN);
        assertThat(admin.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void blankAdminPasswordIsRejectedBeforeAnythingIsWritten() {
        assertThatThrownBy(() -> service.createRestaurant("cafe", RestaurantConfig.of("Café"), "owner", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createRestaurant("cafe", RestaurantConfig.of("Café"), "owner", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(restaurants.size()).isZero();
        assertThat(staff.size()).isZero();
    }

    @Test
    void takenAdminUsernameIsRejectedBeforeTheRestaurantIsWritten() {
        staff.save(StaffAccount.create(new TenantId(UUID.randomUUID()), "owner", Role.STAFF, "hash"));

        assertThatThrownBy(() -> service.createRestaurant("cafe", RestaurantConfig.of("Café"), "Owner", "hash"))
                .isInstanceOf(DuplicateUsernameException.class);
        assertThat(restaurants.size()).isZero();
    }

    // ---- update / deactivate / list ---------------------------------------------------------

    @Test
    void updatesARestaurant() {
        var restaurant = service.createRestaurant("cafe", RestaurantConfig.of("Old"));

        var updated = service.updateRestaurant(restaurant.getTenantId(), RestaurantConfig.of("New"));

        assertThat(updated.getConfig().name()).isEqualTo("New");
        assertThat(restaurants.findByTenantId(restaurant.getTenantId()).orElseThrow().getConfig().name())
                .isEqualTo("New");
    }

    @Test
    void updateOfAnUnknownRestaurantIsNotFound() {
        assertThatThrownBy(() -> service.updateRestaurant(new TenantId(UUID.randomUUID()), RestaurantConfig.of("x")))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void deactivatesARestaurant() {
        var restaurant = service.createRestaurant("cafe", RestaurantConfig.of("Café"));

        var result = service.deactivateRestaurant(restaurant.getTenantId());

        assertThat(result.getStatus()).isEqualTo(RestaurantStatus.DEACTIVATED);
        assertThat(restaurants.findByTenantId(restaurant.getTenantId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void deactivationOfAnUnknownRestaurantIsNotFound() {
        assertThatThrownBy(() -> service.deactivateRestaurant(new TenantId(UUID.randomUUID())))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void listsRestaurantsByIdentifier() {
        service.createRestaurant("b-cafe", RestaurantConfig.of("B"));
        service.createRestaurant("a-cafe", RestaurantConfig.of("A"));

        assertThat(service.listRestaurants()).extracting(Restaurant::getIdentifier)
                .containsExactly("a-cafe", "b-cafe");
    }
}

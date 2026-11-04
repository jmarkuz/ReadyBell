package org.readybell.restaurant.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.readybell.restaurant.domain.AccountStatus;
import org.readybell.restaurant.domain.InMemoryRestaurantRepository;
import org.readybell.restaurant.domain.InMemoryStaffAccountRepository;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.Role;
import org.readybell.restaurant.domain.StaffAccount;
import org.readybell.restaurant.domain.StaffAccountId;
import org.readybell.shared.domain.TenantId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestaurantAdminServiceTest {

    private static final TenantId TENANT = new TenantId(UUID.randomUUID());
    private static final TenantId OTHER = new TenantId(UUID.randomUUID());

    private InMemoryRestaurantRepository restaurants;
    private InMemoryStaffAccountRepository staff;
    private RestaurantAdminService service;

    @BeforeEach
    void setUp() {
        restaurants = new InMemoryRestaurantRepository();
        staff = new InMemoryStaffAccountRepository();
        service = new RestaurantAdminService(restaurants, staff);
        restaurants.save(Restaurant.create(TENANT, "cafe", RestaurantConfig.of("Café")));
    }

    // ---- createStaffAccount -----------------------------------------------------------------

    @Test
    void createsAnAccountWithANormalizedUsername() {
        StaffAccount account = service.createStaffAccount(TENANT, "  Anna ", Role.STAFF, "{bcrypt}hash");

        assertThat(account.getUsername()).isEqualTo("anna");
        assertThat(account.getTenantId()).isEqualTo(TENANT);
        assertThat(account.getRole()).isEqualTo(Role.STAFF);
        assertThat(account.getPasswordHash()).isEqualTo("{bcrypt}hash");
        assertThat(staff.size()).isEqualTo(1);
    }

    @Test
    void usernamesAreUniqueAcrossTenants() {
        service.createStaffAccount(OTHER, "anna", Role.STAFF, "hash");

        assertThatThrownBy(() -> service.createStaffAccount(TENANT, "Anna", Role.STAFF, "hash"))
                .isInstanceOf(DuplicateUsernameException.class);
        assertThat(staff.size()).isEqualTo(1);
    }

    @Test
    void invalidUsernameIsRejected() {
        assertThatThrownBy(() -> service.createStaffAccount(TENANT, " ", Role.STAFF, "hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(staff.size()).isZero();
    }

    @Test
    void createRejectsNullArguments() {
        assertThatThrownBy(() -> service.createStaffAccount(null, "anna", Role.STAFF, "hash"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.createStaffAccount(TENANT, "anna", null, "hash"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.createStaffAccount(TENANT, "anna", Role.STAFF, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- deactivateStaffAccount -------------------------------------------------------------

    @Test
    void deactivatesAnAccountOfTheTenant() {
        var account = service.createStaffAccount(TENANT, "anna", Role.STAFF, "hash");

        var result = service.deactivateStaffAccount(TENANT, account.getId());

        assertThat(result.getStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        assertThat(staff.findByTenantAndId(TENANT, account.getId()).orElseThrow().canSignIn()).isFalse();
    }

    @Test
    void unknownAccountIsNotFound() {
        assertThatThrownBy(() -> service.deactivateStaffAccount(TENANT, StaffAccountId.newId()))
                .isInstanceOf(StaffAccountNotFoundException.class);
    }

    @Test
    void anotherTenantsAccountIsNotFound() {
        var account = service.createStaffAccount(OTHER, "bob", Role.STAFF, "hash");

        assertThatThrownBy(() -> service.deactivateStaffAccount(TENANT, account.getId()))
                .isInstanceOf(StaffAccountNotFoundException.class);
        assertThat(staff.findByTenantAndId(OTHER, account.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void deactivateRejectsNulls() {
        assertThatThrownBy(() -> service.deactivateStaffAccount(null, StaffAccountId.newId()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.deactivateStaffAccount(TENANT, null)).isInstanceOf(NullPointerException.class);
    }

    // ---- restaurant -------------------------------------------------------------------------

    @Test
    void returnsTheRestaurantOfTheTenant() {
        assertThat(service.getRestaurant(TENANT).getIdentifier()).isEqualTo("cafe");
    }

    @Test
    void missingRestaurantIsNotFound() {
        assertThatThrownBy(() -> service.getRestaurant(OTHER)).isInstanceOf(RestaurantNotFoundException.class);
        assertThatThrownBy(() -> service.getRestaurant(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void updatesTheConfig() {
        var updated = service.updateRestaurantConfig(TENANT, RestaurantConfig.of("New name"));

        assertThat(updated.getConfig().name()).isEqualTo("New name");
        assertThat(restaurants.findByTenantId(TENANT).orElseThrow().getConfig().name()).isEqualTo("New name");
    }

    @Test
    void updateOfAMissingRestaurantIsNotFound() {
        assertThatThrownBy(() -> service.updateRestaurantConfig(OTHER, RestaurantConfig.of("x")))
                .isInstanceOf(RestaurantNotFoundException.class);
    }

    @Test
    void updateRejectsNulls() {
        assertThatThrownBy(() -> service.updateRestaurantConfig(null, RestaurantConfig.of("x")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.updateRestaurantConfig(TENANT, null)).isInstanceOf(NullPointerException.class);
    }

    // ---- listStaffAccounts ------------------------------------------------------------------

    @Test
    void listsOnlyTheTenantsAccounts() {
        service.createStaffAccount(TENANT, "anna", Role.STAFF, "hash");
        service.createStaffAccount(OTHER, "bob", Role.STAFF, "hash");

        assertThat(service.listStaffAccounts(TENANT)).extracting(StaffAccount::getUsername).containsExactly("anna");
        assertThatThrownBy(() -> service.listStaffAccounts(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorRejectsNulls() {
        assertThatThrownBy(() -> new RestaurantAdminService(null, staff)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RestaurantAdminService(restaurants, null)).isInstanceOf(NullPointerException.class);
    }
}

package org.readybell.platform;

import org.junit.jupiter.api.Test;
import org.readybell.platform.auth.AccountDirectory;
import org.readybell.restaurant.domain.InMemoryRestaurantRepository;
import org.readybell.restaurant.domain.InMemoryStaffAccountRepository;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.Role;
import org.readybell.restaurant.domain.StaffAccount;
import org.readybell.shared.domain.TenantId;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Usernames are unique within a restaurant: every lookup names the restaurant. */
class AccountDirectoryTest {

    private final InMemoryStaffAccountRepository staff = new InMemoryStaffAccountRepository();
    private final InMemoryRestaurantRepository restaurants = new InMemoryRestaurantRepository();

    private final Restaurant aurora = Restaurant.create(new TenantId(UUID.randomUUID()), "aurora", RestaurantConfig.of("Aurora"));
    private final Restaurant borsch = Restaurant.create(new TenantId(UUID.randomUUID()), "borsch", RestaurantConfig.of("Borsch"));
    private final TenantId devTenant = new TenantId(UUID.randomUUID());

    private final AccountDirectory directory = new AccountDirectory(Map.of(
            "platform", new TenantUser("platform", "{x}", List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")), null),
            "dev", new TenantUser("dev", "{x}", List.of(new SimpleGrantedAuthority("ROLE_STAFF")), devTenant)),
            staff, restaurants);

    AccountDirectoryTest() {
        restaurants.save(aurora);
        restaurants.save(borsch);
        staff.save(StaffAccount.create(aurora.getTenantId(), "admin", Role.RESTAURANT_ADMIN, "hash-a"));
        staff.save(StaffAccount.create(borsch.getTenantId(), "admin", Role.RESTAURANT_ADMIN, "hash-b"));
    }

    @Test
    void theSameUsernameResolvesToTheAccountOfTheNamedRestaurant() {
        var a = directory.findInRestaurant("aurora", "admin").orElseThrow();
        var b = directory.findInRestaurant("borsch", "admin").orElseThrow();

        assertThat(a.getTenantId()).isEqualTo(aurora.getTenantId());
        assertThat(a.getPassword()).isEqualTo("hash-a");
        assertThat(b.getTenantId()).isEqualTo(borsch.getTenantId());
        assertThat(b.getPassword()).isEqualTo("hash-b");
    }

    @Test
    void identifierAndUsernameAreCaseInsensitive() {
        assertThat(directory.findInRestaurant(" Aurora ", " ADMIN ")).isPresent();
    }

    @Test
    void unknownRestaurantOrUsernameFindsNothing() {
        assertThat(directory.findInRestaurant("nowhere", "admin")).isEmpty();
        assertThat(directory.findInRestaurant("aurora", "ghost")).isEmpty();
    }

    @Test
    void withoutARestaurantOnlyThePlatformOperatorSignsIn() {
        assertThat(directory.findInRestaurant(null, "platform")).isPresent();
        assertThat(directory.findInRestaurant("", "platform")).isPresent();
        assertThat(directory.findInRestaurant("", "dev")).isEmpty();          // a configured restaurant user needs its restaurant
        assertThat(directory.findInRestaurant("", "admin")).isEmpty();        // so do database accounts
    }

    @Test
    void aTokenLookupNamesTheTenant() {
        assertThat(directory.find(aurora.getTenantId(), "admin").orElseThrow().getPassword()).isEqualTo("hash-a");
        assertThat(directory.find(null, "admin")).isEmpty();
        assertThat(directory.find(aurora.getTenantId(), "platform")).isEmpty();   // the operator is not a member of a restaurant
    }

    @Test
    void aDeactivatedRestaurantDisablesItsAccounts() {
        aurora.deactivate();
        restaurants.save(aurora);

        assertThat(directory.find(aurora.getTenantId(), "admin").orElseThrow().isEnabled()).isFalse();
        assertThat(directory.find(borsch.getTenantId(), "admin").orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void httpBasicUsesTheBareNameForConfiguredUsersAndRestaurantSlashNameForTheRest() {
        assertThat(directory.loadUserByUsername("platform").getUsername()).isEqualTo("platform");
        assertThat(directory.loadUserByUsername("borsch/admin").getPassword()).isEqualTo("hash-b");
        assertThatThrownBy(() -> directory.loadUserByUsername("admin")).isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> directory.loadUserByUsername("borsch/ghost")).isInstanceOf(UsernameNotFoundException.class);
    }
}

package org.readybell.platform.auth;

import org.readybell.platform.TenantUser;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.restaurant.domain.StaffAccountRepository;
import org.readybell.shared.domain.TenantId;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Every account that can sign in: users configured in {@code readybell.security.users} (platform operators, dev
 * users) and the staff accounts of the database.
 *
 * <p>Usernames are unique <em>within a restaurant</em>, so signing in names the restaurant by its identifier
 * ({@link #findInRestaurant}); the platform operator has no restaurant. A configured user wins over a database
 * account of the same name in the same tenant.
 *
 * <p>It is also the {@link UserDetailsService} behind HTTP Basic (curl, tests): {@code username} for a configured
 * user, {@code restaurant/username} for a database account.
 */
public class AccountDirectory implements AccountLookup, UserDetailsService {

    private final Map<String, TenantUser> configured;
    private final StaffAccountRepository staffAccounts;
    private final RestaurantRepository restaurants;

    /** @param configured the configured users by lower-case username (their names are unique among themselves) */
    public AccountDirectory(Map<String, TenantUser> configured, StaffAccountRepository staffAccounts,
                            RestaurantRepository restaurants) {
        this.configured = Map.copyOf(configured);
        this.staffAccounts = Objects.requireNonNull(staffAccounts);
        this.restaurants = Objects.requireNonNull(restaurants);
    }

    @Override
    public Optional<TenantUser> find(TenantId tenant, String username) {
        String name = normalize(username);
        TenantUser user = configured.get(name);
        if (user != null && Objects.equals(user.getTenantId(), tenant)) {
            // Fresh instance per lookup: Spring Security erases credentials on the returned object.
            return Optional.of(new TenantUser(user.getUsername(), user.getPassword(), user.getAuthorities(),
                    user.getTenantId()));
        }
        if (tenant == null) {
            return Optional.empty();
        }
        return staffAccounts.findByTenantAndUsername(tenant, name).map(account -> {
            boolean restaurantActive = restaurants.findByTenantId(tenant).map(Restaurant::isActive).orElse(false);
            return new TenantUser(
                    account.getUsername(),
                    account.getPasswordHash() == null ? "" : account.getPasswordHash(),
                    account.canSignIn() && restaurantActive,
                    List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())),
                    account.getTenantId());
        });
    }

    /**
     * Sign-in: the account {@code username} of the restaurant with this identifier. Without a restaurant only the
     * platform operator (a configured user without a tenant) can sign in.
     */
    public Optional<TenantUser> findInRestaurant(String restaurantIdentifier, String username) {
        if (restaurantIdentifier == null || restaurantIdentifier.isBlank()) {
            return find(null, username);
        }
        return restaurants.findByIdentifier(restaurantIdentifier.strip().toLowerCase(Locale.ROOT))
                .flatMap(r -> find(r.getTenantId(), username));
    }

    /** HTTP Basic: {@code username} (configured users) or {@code restaurant/username}. */
    @Override
    public UserDetails loadUserByUsername(String basicName) {
        String raw = basicName == null ? "" : basicName.strip();
        int slash = raw.indexOf('/');
        if (slash > 0) {
            return findInRestaurant(raw.substring(0, slash), raw.substring(slash + 1))
                    .orElseThrow(() -> new UsernameNotFoundException(raw));
        }
        TenantUser user = configured.get(normalize(raw));
        if (user == null) {
            throw new UsernameNotFoundException(raw);
        }
        return find(user.getTenantId(), raw).orElseThrow(() -> new UsernameNotFoundException(raw));
    }

    private static String normalize(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}

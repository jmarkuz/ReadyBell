package org.readybell.restaurant.application;

import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.restaurant.domain.Role;
import org.readybell.restaurant.domain.StaffAccount;
import org.readybell.restaurant.domain.StaffAccountId;
import org.readybell.restaurant.domain.StaffAccountRepository;
import org.readybell.shared.domain.TenantId;

import java.util.List;
import java.util.Objects;

/**
 * Application service implementing {@link RestaurantAdminUseCase}.
 *
 * <p>This class is plain Java — no Spring annotations. Constructor injection provides
 * every dependency; wiring happens in the composition root (Spring {@code @Configuration}
 * or equivalent). The domain and adapter layers are referenced only through their port
 * interfaces, preserving the Clean Architecture dependency rule.
 */
public class RestaurantAdminService implements RestaurantAdminUseCase {
    private final RestaurantRepository restaurantRepository;
    private final StaffAccountRepository staffRepository;

    public RestaurantAdminService(RestaurantRepository restaurantRepository,
                                  StaffAccountRepository staffRepository) {
        this.restaurantRepository = Objects.requireNonNull(restaurantRepository,
                "restaurantRepository must not be null");
        this.staffRepository = Objects.requireNonNull(staffRepository,
                "staffRepository must not be null");
    }

    @Override
    public StaffAccount createStaffAccount(TenantId tenantId, String username, Role role, String passwordHash) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        String name = UsernamePolicy.normalize(username);

        // Usernames are unique within the restaurant; other restaurants may use the same name
        staffRepository.findByTenantAndUsername(tenantId, name)
                .ifPresent(existing -> {
                    throw new DuplicateUsernameException(tenantId, name);
                });

        StaffAccount account = StaffAccount.create(tenantId, name, role, passwordHash);

        staffRepository.save(account);
        return account;
    }

    @Override
    public StaffAccount deactivateStaffAccount(TenantId tenantId, StaffAccountId accountId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");

        StaffAccount account = staffRepository.findByTenantAndId(tenantId, accountId)
                .orElseThrow(() -> new StaffAccountNotFoundException(tenantId, accountId));

        account.deactivate();

        staffRepository.save(account);
        return account;
    }

    @Override
    public Restaurant getRestaurant(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return restaurantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RestaurantNotFoundException(tenantId));
    }

    @Override
    public List<StaffAccount> listStaffAccounts(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return staffRepository.findAllByTenant(tenantId);
    }

    @Override
    public Restaurant updateRestaurantConfig(TenantId tenantId, RestaurantConfig config) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(config, "config must not be null");

        Restaurant restaurant = restaurantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RestaurantNotFoundException(tenantId));

        restaurant.updateConfig(config);

        restaurantRepository.save(restaurant);
        return restaurant;
    }
}

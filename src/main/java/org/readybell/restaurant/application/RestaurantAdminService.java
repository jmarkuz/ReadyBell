package org.readybell.restaurant.application;

import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantConfig;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.restaurant.domain.Role;
import org.readybell.restaurant.domain.StaffAccount;
import org.readybell.restaurant.domain.StaffAccountId;
import org.readybell.restaurant.domain.StaffAccountRepository;
import org.readybell.shared.domain.TenantId;

import java.util.Objects;

/**
 * Application service implementing {@link RestaurantAdminUseCase}.
 *
 * <p>This class is plain Java — no Spring annotations. Constructor injection provides
 * every dependency; wiring happens in the composition root (Spring {@code @Configuration}
 * or equivalent). The domain and adapter layers are referenced only through their port
 * interfaces, preserving the Clean Architecture dependency rule.
 *
 * <p>Requirements covered: 8.3, 8.4, 8.5, 8.6, 8.7, 8.8.
 */
public class RestaurantAdminService implements RestaurantAdminUseCase {

    private final RestaurantRepository restaurantRepository;
    private final StaffAccountRepository staffRepository;

    /**
     * @param restaurantRepository repository port for loading and persisting restaurants
     * @param staffRepository      repository port for loading and persisting staff accounts
     */
    public RestaurantAdminService(RestaurantRepository restaurantRepository,
                                  StaffAccountRepository staffRepository) {
        this.restaurantRepository = Objects.requireNonNull(restaurantRepository,
                "restaurantRepository must not be null");
        this.staffRepository = Objects.requireNonNull(staffRepository,
                "staffRepository must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation steps:
     * <ol>
     *   <li>Check for an existing account with the same username within the tenant
     *       (Req 8.4); throw {@link DuplicateUsernameException} if one is found.</li>
     *   <li>Create the account via {@link StaffAccount#create} in {@code ACTIVE} status
     *       (Req 8.3) with the admin's {@code tenantId}.</li>
     *   <li>Persist via {@link StaffAccountRepository#save} and return.</li>
     * </ol>
     */
    @Override
    public StaffAccount createStaffAccount(TenantId tenantId, String username, Role role, String passwordHash) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        String name = UsernamePolicy.normalize(username);

        // Usernames are unique platform-wide because sign-in takes only a username (Req 8.4)
        staffRepository.findByUsername(name)
                .ifPresent(existing -> {
                    throw new DuplicateUsernameException(tenantId, name);
                });

        // Create account (Req 8.3)
        StaffAccount account = StaffAccount.create(tenantId, name, role, passwordHash);

        // Persist and return
        staffRepository.save(account);
        return account;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation steps:
     * <ol>
     *   <li>Load the account scoped to the admin's tenant (Req 8.8); throw
     *       {@link StaffAccountNotFoundException} if absent (covers both "not found"
     *       and cross-tenant access).</li>
     *   <li>Deactivate via {@link StaffAccount#deactivate} (Req 8.5).</li>
     *   <li>Persist via {@link StaffAccountRepository#save} and return.</li>
     * </ol>
     */
    @Override
    public StaffAccount deactivateStaffAccount(TenantId tenantId, StaffAccountId accountId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(accountId, "accountId must not be null");

        // Load account scoped to tenant; cross-tenant returns empty (Req 8.8)
        StaffAccount account = staffRepository.findByTenantAndId(tenantId, accountId)
                .orElseThrow(() -> new StaffAccountNotFoundException(tenantId, accountId));

        // Deactivate (Req 8.5)
        account.deactivate();

        // Persist and return
        staffRepository.save(account);
        return account;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implementation steps:
     * <ol>
     *   <li>Load the restaurant for the admin's tenant (Req 8.8); throw
     *       {@link RestaurantNotFoundException} if absent.</li>
     *   <li>Apply the new configuration via {@link Restaurant#updateConfig} (Req 8.6).</li>
     *   <li>Persist via {@link RestaurantRepository#save} and return.</li>
     * </ol>
     */
    @Override
    public Restaurant updateRestaurantConfig(TenantId tenantId, RestaurantConfig config) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(config, "config must not be null");

        // Load restaurant for tenant (Req 8.8)
        Restaurant restaurant = restaurantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RestaurantNotFoundException(tenantId));

        // Apply configuration update (Req 8.6)
        restaurant.updateConfig(config);

        // Persist and return
        restaurantRepository.save(restaurant);
        return restaurant;
    }
}

package org.readybell.restaurant.domain;

import org.readybell.shared.domain.TenantId;

import java.util.Objects;

/**
 * Aggregate root representing a Restaurant tenant on the ReadyBell Platform.
 *
 * <p>A Restaurant is identified by a unique {@link TenantId} (Req 9.2) and a
 * unique business {@link #identifier} (Req 9.5). Its lifecycle is captured by
 * {@link RestaurantStatus}: a Restaurant starts {@code ACTIVE} and may be
 * {@code DEACTIVATED} by a Platform Admin (Req 9.3, 9.6).
 *
 * <p>This class is plain Java with no framework or persistence annotations, in
 * keeping with the domain-purity design goal.
 */
public class Restaurant {

    private final TenantId tenantId;
    private String identifier;
    private RestaurantStatus status;
    private RestaurantConfig config;

    // -------------------------------------------------------------------------
    // Construction — only via static factory
    // -------------------------------------------------------------------------

    private Restaurant(TenantId tenantId, String identifier, RestaurantConfig config) {
        this.tenantId = tenantId;
        this.identifier = identifier;
        this.status = RestaurantStatus.ACTIVE;
        this.config = config;
    }

    /**
     * Creates a new, active Restaurant.
     *
     * @param tenantId   the unique tenant identifier assigned by the platform (Req 9.2)
     * @param identifier the unique business identifier supplied by the Platform Admin (Req 9.5)
     * @param config     initial restaurant configuration
     * @return a {@link Restaurant} in {@link RestaurantStatus#ACTIVE} status
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if {@code identifier} is blank
     */
    public static Restaurant create(TenantId tenantId, String identifier, RestaurantConfig config) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(identifier, "identifier must not be null");
        Objects.requireNonNull(config, "config must not be null");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        return new Restaurant(tenantId, identifier, config);
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    /**
     * Deactivates the Restaurant.
     *
     * <p>Idempotent: calling this on an already-deactivated Restaurant is a no-op.
     * Platform Admins may call this to remove a tenant from active service (Req 9.3).
     */
    public void deactivate() {
        this.status = RestaurantStatus.DEACTIVATED;
    }

    /**
     * Guards operations that require the Restaurant to be active, specifically
     * the start of a new customer session (Req 9.6).
     *
     * @throws DeactivatedRestaurantException if this Restaurant is {@code DEACTIVATED}
     */
    public void checkCanStartSession() {
        if (status == RestaurantStatus.DEACTIVATED) {
            throw new DeactivatedRestaurantException(identifier);
        }
    }

    /**
     * Updates the restaurant's mutable configuration (Req 8.6).
     *
     * @param newConfig the replacement configuration value object
     * @throws NullPointerException if {@code newConfig} is null
     */
    public void updateConfig(RestaurantConfig newConfig) {
        this.config = Objects.requireNonNull(newConfig, "config must not be null");
    }

    // -------------------------------------------------------------------------
    // Accessors (read-only; no public setters — mutation via behaviour methods)
    // -------------------------------------------------------------------------

    public TenantId getTenantId() {
        return tenantId;
    }

    public String getIdentifier() {
        return identifier;
    }

    public RestaurantStatus getStatus() {
        return status;
    }

    public RestaurantConfig getConfig() {
        return config;
    }

    public boolean isActive() {
        return status == RestaurantStatus.ACTIVE;
    }

    // -------------------------------------------------------------------------
    // Reconstitution — used by the JPA adapter to rebuild the aggregate from a
    // stored entity without applying default state overrides.
    // -------------------------------------------------------------------------

    /**
     * Rebuilds a {@code Restaurant} from its stored state.
     *
     * <p>Unlike {@link #create} this method applies the persisted {@code status}
     * directly rather than defaulting to ACTIVE. Intended for use by the JPA adapter only.
     *
     * @param tenantId   the persisted tenant identity
     * @param identifier the persisted business identifier
     * @param status     the persisted restaurant status
     * @param config     the persisted restaurant configuration
     * @return a restaurant aggregate with the given state
     */
    public static Restaurant reconstitute(TenantId tenantId,
                                          String identifier,
                                          RestaurantStatus status,
                                          RestaurantConfig config) {
        var r = new Restaurant(tenantId, identifier, config);
        r.status = status;
        return r;
    }
}

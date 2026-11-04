package org.readybell.restaurant.adapter.in.web;

import org.readybell.restaurant.application.RestaurantNotFoundException;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own restaurant (name and identifier), for branding the dashboard. Any staff role. */
@RestController
@RequestMapping("/api/restaurant")
public class RestaurantProfileController {

    private final RestaurantRepository restaurants;

    public RestaurantProfileController(RestaurantRepository restaurants) {
        this.restaurants = restaurants;
    }

    @GetMapping
    public Profile profile() {
        TenantId tenantId = TenantContextHolder.require();
        var restaurant = restaurants.findByTenantId(tenantId)
                .orElseThrow(() -> new RestaurantNotFoundException(tenantId));
        return new Profile(restaurant.getConfig().name(), restaurant.getIdentifier());
    }

    public record Profile(String name, String identifier) {}
}

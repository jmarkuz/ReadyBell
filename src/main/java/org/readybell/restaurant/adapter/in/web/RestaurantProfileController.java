package org.readybell.restaurant.adapter.in.web;

import org.readybell.restaurant.application.RestaurantAdminUseCase;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own restaurant (name and identifier), for branding the dashboard. Any staff role. */
@RestController
@RequestMapping("/api/restaurant")
public class RestaurantProfileController {

    private final RestaurantAdminUseCase restaurants;

    public RestaurantProfileController(RestaurantAdminUseCase restaurants) {
        this.restaurants = restaurants;
    }

    @GetMapping
    public Profile profile() {
        Restaurant restaurant = restaurants.getRestaurant(TenantContextHolder.require());
        return new Profile(restaurant.getConfig().name(), restaurant.getIdentifier());
    }

    public record Profile(String name, String identifier) {}
}

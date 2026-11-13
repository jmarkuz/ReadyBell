package org.readybell.ordering.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.readybell.ordering.application.CounterClaimUseCase;
import org.readybell.ordering.domain.OrderAlreadyClaimedException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public endpoints behind the counter QR code ({@code /r/{identifier}} in the customer app). No authentication: the
 * restaurant identifier comes from the QR code and the customer proves nothing more than the receipt number, so the
 * endpoints are rate limited per IP and every failure answers the same 404.
 */
@RestController
@RequestMapping("/api/restaurants/{identifier}")
public class CounterClaimController {

    public record PublicRestaurantResponse(String name) {}

    public record ClaimOrderRequest(@NotNull @Min(1) @Max(999_999) Integer orderNumber) {}

    public record ClaimOrderResponse(UUID sessionId) {}

    private final CounterClaimUseCase counterClaim;

    public CounterClaimController(CounterClaimUseCase counterClaim) {
        this.counterClaim = counterClaim;
    }

    /** The restaurant's display name, so the customer can see they scanned the right counter. */
    @GetMapping("/public")
    public PublicRestaurantResponse restaurant(@PathVariable String identifier) {
        return counterClaim.restaurantName(identifier)
                .map(PublicRestaurantResponse::new)
                .orElseThrow(ClaimUnavailableException::new);
    }

    /** Claims the order with the receipt number and returns the session id used by {@code /track/{sessionId}}. */
    @PostMapping("/claim")
    public ClaimOrderResponse claim(@PathVariable String identifier, @Valid @RequestBody ClaimOrderRequest request) {
        try {
            return counterClaim.claim(identifier, request.orderNumber())
                    .map(session -> new ClaimOrderResponse(session.value()))
                    .orElseThrow(ClaimUnavailableException::new);
        } catch (OptimisticLockingFailureException | OrderAlreadyClaimedException lostTheRace) {
            throw new ClaimUnavailableException();   // another customer claimed it a moment earlier
        }
    }
}

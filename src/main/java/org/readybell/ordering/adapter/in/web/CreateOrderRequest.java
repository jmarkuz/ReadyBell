package org.readybell.ordering.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.readybell.ordering.domain.OrderDetails;
import org.readybell.ordering.domain.OrderItem;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/orders}.
 *
 * <p>{@code sessionId} (alias {@code customerSessionId}) is optional: when absent, the server starts a new
 * customer session for the order (the receipt QR code then links to it). Everything else is optional. {@code estimatedMinutes} is relative to the time of the request.
 */
public record CreateOrderRequest(
        @JsonAlias("customerSessionId") UUID sessionId,
        @Size(max = OrderDetails.MAX_CUSTOMER_NAME) String customerName,
        @Min(0) @Max(24 * 60) Integer estimatedMinutes,
        @Size(max = OrderDetails.MAX_ITEMS) List<@Valid Item> items) {

    /** Convenience for callers that only know the session. */
    public CreateOrderRequest(UUID sessionId) {
        this(sessionId, null, null, null);
    }

    public record Item(
            @NotBlank @Size(max = OrderItem.MAX_NAME_LENGTH) @JsonAlias("menuItemId") String name,
            @Min(1) @Max(OrderItem.MAX_QUANTITY) int quantity) {}

    OrderDetails toDetails(Clock clock) {
        return new OrderDetails(
                customerName,
                items == null ? List.of() : items.stream().map(i -> new OrderItem(i.name(), i.quantity())).toList(),
                estimatedMinutes == null ? null : clock.instant().plus(Duration.ofMinutes(estimatedMinutes)));
    }
}

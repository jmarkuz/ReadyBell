package org.readybell.ordering.adapter.in.web;

import org.readybell.ordering.application.SessionTracking;
import org.readybell.ordering.application.TrackSessionUseCase;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Public, customer-facing view of a session's orders. The unguessable session id in the path is the
 * credential; the tenant is resolved from it by the tenant filter.
 */
@RestController
@RequestMapping("/api/sessions/{id}")
public class CustomerOrdersController {

    private final TrackSessionUseCase tracking;
    private final Clock clock;

    public CustomerOrdersController(TrackSessionUseCase tracking, Clock clock) {
        this.tracking = tracking;
        this.clock = clock;
    }

    /**
     * Orders of the session with their status; an empty list when there are none (Req 2.5).
     */
    @GetMapping("/orders")
    public List<OrderStateResponse> listOrders(@PathVariable("id") UUID id) {
        TenantId tenantId = TenantContextHolder.require();
        return tracking.track(tenantId, new CustomerSessionId(id))
                .map(t -> t.orders().stream().map(OrderStateResponse::from).toList())
                .orElse(List.of());
    }

    /**
     * Data for the tracking page opened from the receipt QR code ({@code /track/{sessionId}}).
     * An unknown session id yields 404.
     */
    @GetMapping("/track")
    public TrackResponse track(@PathVariable("id") UUID id) {
        // the tenant is resolved from the session id; none resolved means the link is not valid
        TenantId tenantId = TenantContextHolder.get().orElseThrow(() -> new SessionUnknownException(id));
        SessionTracking result = tracking.track(tenantId, new CustomerSessionId(id))
                .orElseThrow(() -> new SessionUnknownException(id));
        return TrackResponse.from(result, clock);
    }
}

package org.readybell.platform.ops;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/** Platform operator only (under {@code /api/platform/**}): see what gave up, and try again. */
@RestController
@RequestMapping("/api/platform/delivery")
public class DeliveryProblemsController {
    private final DeliveryProblemsService problems;

    public DeliveryProblemsController(DeliveryProblemsService problems) {
        this.problems = problems;
    }

    /** Parked outbox events and the pushes that failed in the last 24 hours. */
    @GetMapping("/problems")
    public Map<String, Object> list() {
        return problems.problems();
    }

    @PostMapping("/events/{id}/retry")
    public Map<String, Object> retryEvent(@PathVariable UUID id) {
        if (problems.retryEvent(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such parked event");
        }
        return Map.of("retried", true);
    }

    @PostMapping("/pushes/{id}/retry")
    public Map<String, Object> retryPush(@PathVariable UUID id) {
        if (problems.retryPush(id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such failed push, or nothing left to send");
        }
        return Map.of("retried", true);
    }
}

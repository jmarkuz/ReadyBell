package org.readybell.session.adapter.in.web;

import jakarta.validation.Valid;

import org.readybell.platform.GlobalExceptionHandler;
import org.readybell.restaurant.domain.DeactivatedRestaurantException;
import org.readybell.session.application.InvalidQrTokenException;
import org.readybell.session.application.StartSessionUseCase;
import org.readybell.session.domain.CustomerSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Inbound REST adapter for the Customer Session API.
 *
 * <p>Exposes {@code POST /api/sessions}, which starts a new session from a QR token. The customer's view of a session's orders lives in the
 * ordering context ({@code CustomerOrdersController}).
 *
 * <p>Error responses use {@code application/problem+json} (RFC 7807) and are
 * produced by {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private final StartSessionUseCase startSessionUseCase;

    public SessionController(StartSessionUseCase startSessionUseCase) {
        this.startSessionUseCase = startSessionUseCase;
    }

    /**
     * Starts a new customer session identified by the QR-code token.
     *
     * <p>On success returns 201 Created with a {@link SessionResponse} body containing
     * the session id, status, and start timestamp.
     *
     * <p>On failure the {@link GlobalExceptionHandler} maps:
     * <ul>
     *   <li>{@link InvalidQrTokenException} → 422 Unprocessable Entity</li>
     *   <li>{@link DeactivatedRestaurantException} → 422 Unprocessable Entity</li>
     * </ul>
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse startSession(@Valid @RequestBody StartSessionRequest request) {
        CustomerSession session = startSessionUseCase.startSession(request.qrToken());
        return SessionResponse.from(session);
    }
}

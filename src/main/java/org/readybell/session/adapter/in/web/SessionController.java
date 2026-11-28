package org.readybell.session.adapter.in.web;

import jakarta.validation.Valid;

import org.readybell.session.application.StartSessionUseCase;
import org.readybell.session.domain.CustomerSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Inbound REST adapter for the Customer Session API.
 *
 * <p>Exposes {@code POST /api/sessions}, which starts a new session from a QR token. The customer's view of a session's orders lives in the
 * ordering context ({@code CustomerOrdersController}).
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private final StartSessionUseCase startSessionUseCase;

    public SessionController(StartSessionUseCase startSessionUseCase) {
        this.startSessionUseCase = startSessionUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse startSession(@Valid @RequestBody StartSessionRequest request) {
        CustomerSession session = startSessionUseCase.startSession(request.qrToken());
        return SessionResponse.from(session);
    }
}

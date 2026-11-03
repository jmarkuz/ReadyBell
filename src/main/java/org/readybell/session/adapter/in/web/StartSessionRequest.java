package org.readybell.session.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/sessions}.
 *
 * @param qrToken the QR-code payload identifying the restaurant (Req 1.7)
 */
public record StartSessionRequest(@NotBlank String qrToken) {}

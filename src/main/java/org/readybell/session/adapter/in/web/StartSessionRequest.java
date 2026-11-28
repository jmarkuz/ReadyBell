package org.readybell.session.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/sessions}. */
public record StartSessionRequest(@NotBlank String qrToken) {}

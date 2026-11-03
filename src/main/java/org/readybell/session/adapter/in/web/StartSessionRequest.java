package org.readybell.session.adapter.in.web;

/**
 * Request body for {@code POST /api/sessions}.
 *
 * @param qrToken the QR-code payload identifying the restaurant (Req 1.7)
 */
public record StartSessionRequest(String qrToken) {}

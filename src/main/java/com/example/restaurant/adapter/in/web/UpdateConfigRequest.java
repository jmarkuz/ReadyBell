package com.example.restaurant.adapter.in.web;

/**
 * Request body for {@code PUT /api/admin/config} (Req 8.6).
 *
 * @param name the new display name for the restaurant
 */
public record UpdateConfigRequest(String name) {}

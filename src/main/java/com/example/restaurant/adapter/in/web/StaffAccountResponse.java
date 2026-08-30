package com.example.restaurant.adapter.in.web;

import com.example.restaurant.domain.StaffAccount;

import java.util.UUID;

/**
 * Response body for staff account operations (Req 8.3, 8.5, 8.8).
 *
 * @param accountId the staff account identifier
 * @param username  the account's username
 * @param role      the account's role (e.g. {@code "STAFF"} or {@code "RESTAURANT_ADMIN"})
 * @param status    the account's lifecycle status (e.g. {@code "ACTIVE"} or {@code "DEACTIVATED"})
 */
public record StaffAccountResponse(UUID accountId, String username, String role, String status) {

    /**
     * Builds a response from the given domain object.
     *
     * @param account the staff account aggregate
     * @return a response record populated from the account's state
     */
    public static StaffAccountResponse from(StaffAccount account) {
        return new StaffAccountResponse(
                account.getId().value(),
                account.getUsername(),
                account.getRole().name(),
                account.getStatus().name()
        );
    }
}

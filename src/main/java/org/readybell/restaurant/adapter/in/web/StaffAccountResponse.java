package org.readybell.restaurant.adapter.in.web;

import org.readybell.restaurant.domain.StaffAccount;

import java.util.UUID;

/** Response body for staff account operations. */
public record StaffAccountResponse(UUID accountId, String username, String role, String status) {
    public static StaffAccountResponse from(StaffAccount account) {
        return new StaffAccountResponse(
                account.getId().value(),
                account.getUsername(),
                account.getRole().name(),
                account.getStatus().name()
        );
    }
}

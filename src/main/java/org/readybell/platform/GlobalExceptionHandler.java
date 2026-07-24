package org.readybell.platform;

import org.readybell.ordering.application.CrossTenantAccessException;
import org.readybell.ordering.application.InvalidSessionException;
import org.readybell.ordering.application.OrderNotFoundException;
import org.readybell.ordering.domain.InvalidTransitionException;
import org.readybell.ordering.domain.TerminalStateException;
import org.readybell.platformadmin.application.DuplicateRestaurantIdentifierException;
import org.readybell.restaurant.application.DuplicateUsernameException;
import org.readybell.restaurant.application.StaffAccountNotFoundException;
import org.readybell.restaurant.domain.DeactivatedRestaurantException;
import org.readybell.session.application.InvalidQrTokenException;
import org.readybell.shared.tenancy.MissingTenantContextException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler that maps application exceptions to RFC 7807
 * Problem Details JSON responses.
 *
 * <p>All error responses use {@code Content-Type: application/problem+json} and
 * carry the fields {@code type}, {@code title}, {@code status}, and {@code detail}
 * as required by RFC 7807.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final MediaType PROBLEM_JSON =
            MediaType.parseMediaType("application/problem+json");

    /**
     * Handles invalid or unresolvable QR tokens (Req 1.4, 1.7).
     *
     * @return 400 Bad Request with a problem detail describing the invalid token
     */
    @ExceptionHandler(InvalidQrTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidQrToken(InvalidQrTokenException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "https://readybell.org/problems/invalid-qr-token",
                "Invalid QR Token",
                ex.getMessage()
        );
    }

    /**
     * Handles attempts to start sessions at a deactivated restaurant (Req 9.6).
     *
     * @return 400 Bad Request with a problem detail describing the deactivated restaurant
     */
    @ExceptionHandler(DeactivatedRestaurantException.class)
    public ResponseEntity<Map<String, Object>> handleDeactivatedRestaurant(DeactivatedRestaurantException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "https://readybell.org/problems/deactivated-restaurant",
                "Restaurant Deactivated",
                ex.getMessage()
        );
    }

    /**
     * Handles requests that arrive without a resolved tenant context (Req 10.5).
     *
     * @return 403 Forbidden with a problem detail indicating the missing tenant context
     */
    @ExceptionHandler(MissingTenantContextException.class)
    public ResponseEntity<Map<String, Object>> handleMissingTenantContext(MissingTenantContextException ex) {
        return problem(
                HttpStatus.FORBIDDEN,
                "https://readybell.org/problems/missing-tenant-context",
                "Missing Tenant Context",
                ex.getMessage()
        );
    }

    /**
     * Handles duplicate username within a tenant (Req 8.4).
     *
     * @return 422 Unprocessable Entity with a problem detail identifying the duplicate field
     */
    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateUsername(DuplicateUsernameException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "https://readybell.org/problems/duplicate-username",
                "Duplicate Username",
                ex.getMessage()
        );
    }

    /**
     * Handles a staff account not found (or cross-tenant) lookup (Req 8.8).
     *
     * @return 404 Not Found with a problem detail
     */
    @ExceptionHandler(StaffAccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleStaffAccountNotFound(StaffAccountNotFoundException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "https://readybell.org/problems/staff-account-not-found",
                "Staff Account Not Found",
                ex.getMessage()
        );
    }

    /**
     * Handles a restaurant not found within the admin's tenant (Req 8.8).
     *
     * @return 404 Not Found with a problem detail
     */
    @ExceptionHandler(org.readybell.restaurant.application.RestaurantNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRestaurantAdminNotFound(
            org.readybell.restaurant.application.RestaurantNotFoundException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "https://readybell.org/problems/restaurant-not-found",
                "Restaurant Not Found",
                ex.getMessage()
        );
    }

    /**
     * Handles a duplicate restaurant identifier on creation (Req 9.5).
     *
     * @return 422 Unprocessable Entity with a problem detail identifying the duplicate field
     */
    @ExceptionHandler(DuplicateRestaurantIdentifierException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateRestaurantIdentifier(
            DuplicateRestaurantIdentifierException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "https://readybell.org/problems/duplicate-restaurant-identifier",
                "Duplicate Restaurant Identifier",
                ex.getMessage()
        );
    }

    /**
     * Handles a restaurant not found for platform admin operations (Req 9.3).
     *
     * @return 404 Not Found with a problem detail
     */
    @ExceptionHandler(org.readybell.platformadmin.application.RestaurantNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePlatformAdminRestaurantNotFound(
            org.readybell.platformadmin.application.RestaurantNotFoundException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "https://readybell.org/problems/restaurant-not-found",
                "Restaurant Not Found",
                ex.getMessage()
        );
    }

    /**
     * Handles requests referencing an invalid or inactive customer session (Req 4.5).
     *
     * @return 422 Unprocessable Entity with a problem detail identifying the invalid session
     */
    @ExceptionHandler(InvalidSessionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSession(InvalidSessionException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "https://readybell.org/problems/invalid-session",
                "Invalid Session",
                ex.getMessage()
        );
    }

    /**
     * Handles cross-tenant access attempts on order creation (Req 4.6, 7.7).
     *
     * @return 403 Forbidden with a problem detail identifying the inaccessible session
     */
    @ExceptionHandler(CrossTenantAccessException.class)
    public ResponseEntity<Map<String, Object>> handleCrossTenantAccess(CrossTenantAccessException ex) {
        return problem(
                HttpStatus.FORBIDDEN,
                "https://readybell.org/problems/cross-tenant-access",
                "Cross-Tenant Access Denied",
                ex.getMessage()
        );
    }

    /**
     * Handles order not found (or cross-tenant silenced) lookups (Req 7.7, 10.3).
     *
     * @return 404 Not Found with a problem detail
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(OrderNotFoundException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "https://readybell.org/problems/order-not-found",
                "Order Not Found",
                ex.getMessage()
        );
    }

    /**
     * Handles disallowed or self-transitions (Req 5.3, 5.4).
     *
     * @return 422 Unprocessable Entity identifying the invalid transition
     */
    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(InvalidTransitionException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "https://readybell.org/problems/invalid-transition",
                "Invalid Order Status Transition",
                ex.getMessage()
        );
    }

    /**
     * Handles transition attempts on already-terminal orders (Req 5.7).
     *
     * @return 422 Unprocessable Entity identifying the terminal state
     */
    @ExceptionHandler(TerminalStateException.class)
    public ResponseEntity<Map<String, Object>> handleTerminalState(TerminalStateException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "https://readybell.org/problems/terminal-order-state",
                "Order In Terminal State",
                ex.getMessage()
        );
    }

    /**
     * Catch-all handler for any unexpected exception.
     *
     * @return 500 Internal Server Error with a generic problem detail
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "https://readybell.org/problems/internal-error",
                "Internal Server Error",
                "An unexpected error occurred."
        );
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> problem(HttpStatus status,
                                                        String type,
                                                        String title,
                                                        String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type",   type);
        body.put("title",  title);
        body.put("status", status.value());
        body.put("detail", detail);
        return ResponseEntity
                .status(status)
                .contentType(PROBLEM_JSON)
                .body(body);
    }
}

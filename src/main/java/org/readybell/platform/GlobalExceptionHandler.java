package org.readybell.platform;

import org.readybell.restaurant.application.RestaurantNotFoundException;
import org.readybell.ordering.application.CrossTenantAccessException;
import org.readybell.ordering.application.InvalidSessionException;
import org.readybell.ordering.application.OrderNotFoundException;
import org.readybell.subscription.domain.PushEndpoint;
import org.readybell.ordering.domain.InvalidTransitionException;
import org.readybell.ordering.domain.TerminalStateException;
import org.readybell.restaurant.application.DuplicateUsernameException;
import org.readybell.restaurant.application.StaffAccountNotFoundException;
import org.readybell.restaurant.domain.DeactivatedRestaurantException;
import org.readybell.session.application.InvalidQrTokenException;
import org.readybell.shared.tenancy.MissingTenantContextException;
import org.readybell.subscription.application.SessionNotActiveException;
import org.readybell.subscription.application.SubscriptionNotFoundException;
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

    @ExceptionHandler(InvalidQrTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidQrToken(InvalidQrTokenException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "https://readybell.org/problems/invalid-qr-token",
                "Invalid QR Token",
                ex.getMessage()
        );
    }

    @ExceptionHandler(DeactivatedRestaurantException.class)
    public ResponseEntity<Map<String, Object>> handleDeactivatedRestaurant(DeactivatedRestaurantException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "https://readybell.org/problems/deactivated-restaurant",
                "Restaurant Deactivated",
                ex.getMessage()
        );
    }

    @ExceptionHandler(MissingTenantContextException.class)
    public ResponseEntity<Map<String, Object>> handleMissingTenantContext(MissingTenantContextException ex) {
        return problem(
                HttpStatus.FORBIDDEN,
                "https://readybell.org/problems/missing-tenant-context",
                "Missing Tenant Context",
                ex.getMessage()
        );
    }

    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateUsername(DuplicateUsernameException ex) {
        return problem(
                HttpStatus.CONFLICT,
                "https://readybell.org/problems/duplicate-username",
                "Duplicate Username",
                ex.getMessage()
        );
    }

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
    @ExceptionHandler(RestaurantNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRestaurantAdminNotFound(
            RestaurantNotFoundException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "https://readybell.org/problems/restaurant-not-found",
                "Restaurant Not Found",
                ex.getMessage()
        );
    }

    @ExceptionHandler(InvalidSessionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSession(InvalidSessionException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "https://readybell.org/problems/invalid-session",
                "Invalid Session",
                ex.getMessage()
        );
    }

    @ExceptionHandler(CrossTenantAccessException.class)
    public ResponseEntity<Map<String, Object>> handleCrossTenantAccess(CrossTenantAccessException ex) {
        return problem(
                HttpStatus.FORBIDDEN,
                "https://readybell.org/problems/cross-tenant-access",
                "Cross-Tenant Access Denied",
                ex.getMessage()
        );
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(OrderNotFoundException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "https://readybell.org/problems/order-not-found",
                "Order Not Found",
                ex.getMessage()
        );
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(InvalidTransitionException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "https://readybell.org/problems/invalid-transition",
                "Invalid Order Status Transition",
                ex.getMessage()
        );
    }

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
     * Handles subscription registration with missing or blank endpoint/key fields (Req 3.3).
     *
     * <p>The {@link PushEndpoint} compact constructor
     * throws {@link IllegalArgumentException} when any of its required fields is null or
     * blank, so we surface that as a 400 Bad Request with an RFC 7807 problem detail that
     * identifies the offending field.
     *
     * @return 400 Bad Request with a problem detail describing the missing data
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "https://readybell.org/problems/invalid-subscription-request",
                "Invalid Subscription Request",
                ex.getMessage()
        );
    }

    /**
     * Handles registration attempts against a session that does not exist or is not
     * active for the given tenant (Req 3.1).
     *
     * @return 422 Unprocessable Entity with a problem detail identifying the inactive/missing session
     */
    @ExceptionHandler(SessionNotActiveException.class)
    public ResponseEntity<Map<String, Object>> handleSessionNotActive(SessionNotActiveException ex) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "https://readybell.org/problems/session-not-active",
                "Session Not Active",
                ex.getMessage()
        );
    }

    /**
     * Handles delete requests for a subscription that does not exist or belongs to a
     * different tenant (Req 3.5, 10.3).
     *
     * @return 404 Not Found with a problem detail
     */
    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSubscriptionNotFound(SubscriptionNotFoundException ex) {
        return problem(
                HttpStatus.NOT_FOUND,
                "https://readybell.org/problems/subscription-not-found",
                "Subscription Not Found",
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

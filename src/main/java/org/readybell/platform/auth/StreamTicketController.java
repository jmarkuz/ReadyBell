package org.readybell.platform.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.readybell.platform.TenantUser;

import java.time.Duration;

/** Issues the single-use ticket that opens {@code GET /api/orders/stream} (see {@link StreamTicketStore}). */
@RestController
public class StreamTicketController {

    /** How long a ticket can be redeemed. */
    public static final Duration TTL = Duration.ofSeconds(30);

    private final StreamTicketStore tickets;
    private final JwtService jwtService;

    public StreamTicketController(StreamTicketStore tickets, JwtService jwtService) {
        this.tickets = tickets;
        this.jwtService = jwtService;
    }

    /**
     * @return 200 with {@code {ticket, expiresIn}}; the ticket is bound to the caller's identity and tenant
     */
    @PostMapping("/api/orders/stream/ticket")
    public TicketResponse issue(Authentication authentication) {
        String jwt;
        if (authentication.getCredentials() instanceof Jwt token) {
            jwt = token.getTokenValue();
        } else if (authentication.getPrincipal() instanceof TenantUser user) {
            jwt = jwtService.issue(user).token();            // HTTP Basic callers (curl, tests)
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return new TicketResponse(tickets.issue(jwt, TTL), TTL.toSeconds());
    }

    public record TicketResponse(String ticket, long expiresIn) {}
}

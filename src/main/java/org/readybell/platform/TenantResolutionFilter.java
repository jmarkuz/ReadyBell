package org.readybell.platform;

import org.readybell.shared.tenancy.MissingTenantContextException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.readybell.session.domain.CustomerSessionId;
import org.readybell.session.domain.CustomerSessionRepository;
import org.readybell.shared.domain.TenantId;
import org.readybell.shared.tenancy.TenantContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inbound filter that resolves the request's {@link TenantId} and stores it in the
 * thread-bound {@link TenantContextHolder} for the duration of the request.
 *
 * <h2>Resolution strategy (phased)</h2>
 * <ol>
 *   <li><strong>Public / health endpoints</strong> — skipped entirely; no tenant required.
 *   <li><strong>Authenticated principal</strong> — a {@link TenantUser} supplies the tenant.
 *       The {@code X-Tenant-Id} header is ignored for resolution; if it names a different
 *       tenant than the principal's the request is rejected with 403.
 *   <li><strong>Customer session paths</strong> ({@code /api/sessions/{id}/**}) — extracts
 *       the session id from the path, looks up the session via
 *       {@link CustomerSessionRepository}, and derives the tenant from the session.
 *   <li><strong>Absent header / unresolvable session</strong> — the request continues
 *       without a resolved tenant.  Any subsequent call to
 *       {@link TenantContextHolder#require()} will throw
 *       {@link MissingTenantContextException} and reject the
 *       access at the repository layer.
 * </ol>
 */
@Component
@Order(1)
public class TenantResolutionFilter extends OncePerRequestFilter {
    /** Legacy header; only checked against the principal's tenant, never trusted. */
    static final String TENANT_ID_HEADER = "X-Tenant-Id";

    private static final String[] PUBLIC_PATH_PREFIXES = {
        "/actuator",
        "/api/platform",
        "/api/login",
        "/api/auth",
        "/api/push",
        "/error"
    };

    /** Session start has no existing session to derive a tenant from. */
    private static final String SESSION_START_PATH = "/api/sessions";

    private static final Pattern SESSION_SUB_PATH =
            Pattern.compile("^/api/sessions/([^/]+)/.*$");

    private final CustomerSessionRepository customerSessionRepository;

    public TenantResolutionFilter(CustomerSessionRepository customerSessionRepository) {
        this.customerSessionRepository = customerSessionRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        for (String prefix : PUBLIC_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }

        // Skip the session-start POST itself (no session exists yet)
        if ("POST".equalsIgnoreCase(request.getMethod())
                && SESSION_START_PATH.equals(path)) {
            return true;
        }

        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (isCrossTenantHeader(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant mismatch");
                return;
            }
            resolveTenant(request);
            filterChain.doFilter(request, response);
        } finally {
            // Always clear to prevent tenant leaking onto pooled threads.
            TenantContextHolder.clear();
        }
    }

    /** Attempts to resolve the tenant for the current request and, on success, calls {@link TenantContextHolder#set(TenantId)}. */
    private void resolveTenant(HttpServletRequest request) {
        // 1. Authenticated principal: the tenant is fixed server-side and the client-supplied
        // header can never override it.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof TenantUser user
                && user.getTenantId() != null) {
            TenantContextHolder.set(user.getTenantId());
            return;
        }

        String path = request.getRequestURI();
        Matcher m = SESSION_SUB_PATH.matcher(path);
        if (m.matches()) {
            UUID sessionUuid = tryParseUuid(m.group(1));
            if (sessionUuid != null) {
                CustomerSessionId sessionId = new CustomerSessionId(sessionUuid);
                customerSessionRepository.findById(sessionId)
                        .ifPresent(session -> TenantContextHolder.set(session.getTenantId()));
            }
        }

        // If neither strategy resolved a tenant, the holder stays empty.
        // TenantContextHolder.require() will reject when any tenant-scoped access is attempted.
    }

    private static boolean isCrossTenantHeader(HttpServletRequest request) {
        String header = request.getHeader(TENANT_ID_HEADER);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (header == null || auth == null || !(auth.getPrincipal() instanceof TenantUser user)
                || user.getTenantId() == null) {
            return false;
        }
        UUID parsed = tryParseUuid(header);
        return parsed == null || !parsed.equals(user.getTenantId().value());
    }

    private static UUID tryParseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

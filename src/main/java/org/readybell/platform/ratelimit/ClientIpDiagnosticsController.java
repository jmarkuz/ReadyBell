package org.readybell.platform.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the platform operator check how the server sees a client once a reverse proxy or tunnel is in front of it:
 * call it through the public URL and compare {@code clientIp} with your own address. If {@code clientIp} equals the
 * proxy ({@code peer}), {@code RATE_LIMIT_TRUSTED_PROXIES} is missing or wrong and every visitor shares one rate-limit
 * and login-lockout budget. Protected by the {@code /api/platform/**} rule (PLATFORM_ADMIN).
 */
@RestController
@RequestMapping("/api/platform/diagnostics")
public class ClientIpDiagnosticsController {

    public record ClientIpReport(String clientIp, String peer, String forwardedFor, boolean trustedProxiesConfigured,
                                 boolean peerIsTrustedProxy) {}

    private final ClientIpResolver ips;

    public ClientIpDiagnosticsController(ClientIpResolver ips) {
        this.ips = ips;
    }

    @GetMapping("/client-ip")
    public ClientIpReport clientIp(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        return new ClientIpReport(ips.resolve(request), peer, request.getHeader("X-Forwarded-For"),
                ips.hasTrustedProxies(), ips.isTrustedProxy(peer));
    }
}

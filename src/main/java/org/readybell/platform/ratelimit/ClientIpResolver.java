package org.readybell.platform.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client address behind reverse proxies. {@code X-Forwarded-For} is believed only when the direct peer is a
 * trusted proxy; the chain is read from the right and the first address that is not a trusted proxy is the
 * client (anything further left is client-supplied and spoofable).
 */
public class ClientIpResolver {
    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private final List<IpAddressMatcher> trusted;
    private final AtomicBoolean warnedAboutUntrustedForwarding = new AtomicBoolean(false);

    public ClientIpResolver(List<String> trustedProxies) {
        this.trusted = trustedProxies.stream().map(String::strip).filter(s -> !s.isEmpty())
                .map(IpAddressMatcher::new).toList();
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!isTrusted(peer)) {
            if (request.getHeader("X-Forwarded-For") != null && warnedAboutUntrustedForwarding.compareAndSet(false, true)) {
                // The classic misconfiguration: behind a proxy or tunnel every client then shares the proxy's address
                // so one visitor can exhaust the limits (and the login lockout) of everyone
                log.warn("A request from {} carries X-Forwarded-For, but {} is not in readybell.rate-limit.trusted-proxies: "
                        + "rate limits and the login lockout treat that address as the client. Behind a reverse proxy or "
                        + "tunnel set RATE_LIMIT_TRUSTED_PROXIES to the proxy's address or network "
                        + "(GET /api/platform/diagnostics/client-ip shows what the server sees).", peer, peer);
            }
            return peer;
        }
        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.isBlank()) {
            return peer;
        }
        String[] chain = header.split(",");
        for (int i = chain.length - 1; i >= 0; i--) {
            String hop = chain[i].strip();
            if (!hop.isEmpty() && !isTrusted(hop)) {
                return hop;
            }
        }
        return peer;
    }

    public boolean hasTrustedProxies() {
        return !trusted.isEmpty();
    }

    public boolean isTrustedProxy(String address) {
        return isTrusted(address);
    }

    boolean hasWarnedAboutUntrustedForwarding() {
        return warnedAboutUntrustedForwarding.get();
    }

    private boolean isTrusted(String address) {
        for (IpAddressMatcher m : trusted) {
            try {
                if (m.matches(address)) return true;
            } catch (IllegalArgumentException e) {
                return false;   // not an IP literal
            }
        }
        return false;
    }
}

package org.readybell.platform.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.List;

/**
 * Client address behind reverse proxies. {@code X-Forwarded-For} is believed only when the direct peer is a
 * trusted proxy; the chain is read from the right and the first address that is not a trusted proxy is the
 * client (anything further left is client-supplied and spoofable).
 */
public class ClientIpResolver {

    private final List<IpAddressMatcher> trusted;

    public ClientIpResolver(List<String> trustedProxies) {
        this.trusted = trustedProxies.stream().map(String::strip).filter(s -> !s.isEmpty())
                .map(IpAddressMatcher::new).toList();
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!isTrusted(peer)) {
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

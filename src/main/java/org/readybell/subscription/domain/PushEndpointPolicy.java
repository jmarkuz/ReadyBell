package org.readybell.subscription.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Which push endpoints the server is willing to call. The server sends an HTTP request to the endpoint URL a browser
 * hands over, so an unrestricted URL is a server-side request forgery: an anonymous visitor could aim the server at
 * internal addresses ({@code http://10.0.0.5/...}, cloud metadata, {@code localhost}).
 *
 * <p>The rule is an allow-list of the real push services' host names plus structural checks: {@code https} only, no
 * credentials in the URL, the default port, a DNS name (never an IP literal), and bounded lengths. Because hosts are
 * matched by name against services we trust, no DNS lookup of attacker-controlled names is involved.
 */
public final class PushEndpointPolicy {
    /** Chrome/Android (FCM), Firefox, Safari/iOS and Edge/Windows push services. A leading {@code *.} means any subdomain. */
    public static final List<String> DEFAULT_ALLOWED_HOSTS = List.of(
            "fcm.googleapis.com",
            "updates.push.services.mozilla.com",
            "*.push.services.mozilla.com",
            "web.push.apple.com",
            "*.push.apple.com",
            "*.notify.windows.com");

    public static final int MAX_ENDPOINT_LENGTH = 2048;
    public static final int MAX_P256DH_LENGTH = 200;
    public static final int MAX_AUTH_LENGTH = 100;

    private final List<String> allowedHosts;

    public PushEndpointPolicy(List<String> extraAllowedHosts) {
        var hosts = new java.util.ArrayList<>(DEFAULT_ALLOWED_HOSTS);
        Objects.requireNonNull(extraAllowedHosts, "extraAllowedHosts must not be null").stream()
                .map(h -> h.strip().toLowerCase(Locale.ROOT))
                .filter(h -> !h.isEmpty())
                .forEach(hosts::add);
        this.allowedHosts = List.copyOf(hosts);
    }

    public static PushEndpointPolicy defaults() {
        return new PushEndpointPolicy(List.of());
    }

    /** @throws InvalidPushEndpointException if the endpoint or the keys are not acceptable */
    public void validate(String endpointUrl, String p256dhKey, String authKey) {
        validateEndpoint(endpointUrl);
        if (p256dhKey != null && p256dhKey.length() > MAX_P256DH_LENGTH) {
            throw new InvalidPushEndpointException("p256dhKey is too long");
        }
        if (authKey != null && authKey.length() > MAX_AUTH_LENGTH) {
            throw new InvalidPushEndpointException("authKey is too long");
        }
    }

    /** @throws InvalidPushEndpointException with a reason that is safe to show to the client */
    public void validateEndpoint(String endpointUrl) {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new InvalidPushEndpointException("endpointUrl must not be blank");
        }
        if (endpointUrl.length() > MAX_ENDPOINT_LENGTH) {
            throw new InvalidPushEndpointException("endpointUrl is too long");
        }
        URI uri;
        try {
            uri = new URI(endpointUrl.strip());
        } catch (URISyntaxException e) {
            throw new InvalidPushEndpointException("endpointUrl is not a valid URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new InvalidPushEndpointException("endpointUrl must use https");
        }
        if (uri.getUserInfo() != null) {
            throw new InvalidPushEndpointException("endpointUrl must not contain credentials");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new InvalidPushEndpointException("endpointUrl must use the default https port");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || looksLikeIpAddress(host)) {
            throw new InvalidPushEndpointException("endpointUrl must name a push service host");
        }
        if (!isAllowedHost(host)) {
            throw new InvalidPushEndpointException("endpointUrl is not a supported push service");
        }
    }

    public boolean isAllowed(String endpointUrl) {
        try {
            validateEndpoint(endpointUrl);
            return true;
        } catch (InvalidPushEndpointException e) {
            return false;
        }
    }

    /** Host name for logs: never the whole URL, whose path is a secret that lets anyone push to that device. */
    public static String hostForLog(String endpointUrl) {
        try {
            String host = new URI(endpointUrl.strip()).getHost();
            return host == null ? "(unknown host)" : host;
        } catch (Exception e) {
            return "(unparseable)";
        }
    }

    private boolean isAllowedHost(String rawHost) {
        String host = rawHost.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        for (String allowed : allowedHosts) {
            if (allowed.startsWith("*.")) {
                String suffix = allowed.substring(1);   // ".push.apple.com"
                if (host.endsWith(suffix) && host.length() > suffix.length()) {
                    return true;
                }
            } else if (host.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeIpAddress(String host) {
        if (host.contains(":") || host.startsWith("[")) {
            return true;
        }
        return host.chars().allMatch(c -> Character.isDigit(c) || c == '.') || host.toLowerCase(Locale.ROOT).startsWith("0x");
    }
}

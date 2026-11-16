package org.readybell.platform.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.readybell.platform.auth.FailureStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Per-IP sliding-window limits for the public endpoints. A request over the limit gets
 * {@code 429} with {@code Retry-After} and a {@code ProblemDetail} body; rejections are counted in
 * {@code ratelimit.rejected{endpoint}}. Requests that matched a rule always count, so a client that keeps
 * hammering stays blocked.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final ClientIpResolver ips;
    private final FailureStore store;
    private final Clock clock;
    private final MeterRegistry meters;
    private static final String SESSION_PATH = "/api/sessions/{sessionId}/**";
    private final AntPathMatcher paths = new AntPathMatcher();
    /**
     * Resolves the path the way Spring MVC will route it: percent-decoded, {@code ;params} and doubled slashes removed.
     * Matching the raw request URI let {@code /api/%73essions} reach the controller without ever meeting a rule.
     */
    private final UrlPathHelper pathHelper = new UrlPathHelper();

    public RateLimitFilter(RateLimitProperties props, ClientIpResolver ips, FailureStore store, Clock clock,
                           MeterRegistry meters) {
        this.props = props;
        this.ips = ips;
        this.store = store;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = pathHelper.getPathWithinApplication(request);
        var applicable = props.getRules().stream().filter(r -> matches(r, request, path)).toList();
        if (applicable.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        Instant now = clock.instant();
        // Check every matching rule first; count the request only if all of them let it through
        var keys = new java.util.ArrayList<String>();
        for (var rule : applicable) {
            String key = rule.getName() + ":" + keyFor(rule, request, path);
            keys.add(key);
            Duration window = rule.getWindow();
            var current = store.window(key, now, window);
            if (current.count() >= rule.getLimit()) {
                long wait = current.oldest().map(o -> Duration.between(now, o.plus(window)).toSeconds() + 1).orElse(1L);
                reject(response, rule, Math.max(1, wait));
                return;
            }
        }
        for (int i = 0; i < applicable.size(); i++) {
            store.add(keys.get(i), now, applicable.get(i).getWindow());
        }
        chain.doFilter(request, response);
    }

    private String keyFor(RateLimitProperties.Rule rule, HttpServletRequest request, String path) {
        return switch (rule.getKeyBy()) {
            case GLOBAL -> "*";
            case SESSION -> {
                if (paths.match(SESSION_PATH, path)) {
                    yield "session:" + paths.extractUriTemplateVariables(SESSION_PATH, path).get("sessionId").toLowerCase(java.util.Locale.ROOT);
                }
                yield ips.resolve(request);      // not a session path: fall back to the address
            }
            case IP -> ips.resolve(request);
        };
    }

    private boolean matches(RateLimitProperties.Rule rule, HttpServletRequest request, String path) {
        return (rule.getMethod().isBlank() || rule.getMethod().equalsIgnoreCase(request.getMethod()))
                && paths.match(rule.getPath(), path);
    }

    private void reject(HttpServletResponse response, RateLimitProperties.Rule rule, long retryAfterSeconds)
            throws IOException {
        meters.counter("ratelimit.rejected", "endpoint", rule.getName()).increment();
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"urn:readybell:rate-limit:exceeded\",\"title\":\"Too Many Requests\","
                + "\"status\":429,\"detail\":\"Too many requests, retry in " + retryAfterSeconds + " seconds.\"}");
    }
}

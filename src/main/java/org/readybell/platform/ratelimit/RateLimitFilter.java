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
    private final AntPathMatcher paths = new AntPathMatcher();

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
        var rule = props.getRules().stream().filter(r -> matches(r, request)).findFirst().orElse(null);
        if (rule == null) {
            chain.doFilter(request, response);
            return;
        }
        String key = rule.getName() + ":" + ips.resolve(request);
        Instant now = clock.instant();
        Duration window = rule.getWindow();
        var current = store.window(key, now, window);
        if (current.count() >= rule.getLimit()) {
            long wait = current.oldest().map(o -> Duration.between(now, o.plus(window)).toSeconds() + 1).orElse(1L);
            reject(response, rule, Math.max(1, wait));
            return;
        }
        store.add(key, now, window);
        chain.doFilter(request, response);
    }

    private boolean matches(RateLimitProperties.Rule rule, HttpServletRequest request) {
        return (rule.getMethod().isBlank() || rule.getMethod().equalsIgnoreCase(request.getMethod()))
                && paths.match(rule.getPath(), request.getRequestURI());
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

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
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Throttles guessing of receipt numbers at {@code POST /api/restaurants/{identifier}/claim}.
 *
 * <p>Only <em>failed</em> claims count (the endpoint answers every failure with the same 404, or 400 for junk), once per
 * client address and once per restaurant. A queue of customers at one restaurant Wi-Fi, who all succeed, never meets the
 * limit; a guesser meets it after a handful of tries, and a distributed guesser meets the restaurant-wide limit. Claim
 * failures are also exported as the {@code claim.failures} metric so an attack shows up in monitoring.
 */
public class ClaimFailureFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATHS = new AntPathMatcher();
    private static final String PATTERN = "/api/restaurants/{identifier}/claim";

    private final RateLimitProperties.Claim limits;
    private final ClientIpResolver ips;
    private final FailureStore store;
    private final Clock clock;
    private final MeterRegistry meters;
    private final UrlPathHelper pathHelper = new UrlPathHelper();

    public ClaimFailureFilter(RateLimitProperties.Claim limits, ClientIpResolver ips, FailureStore store, Clock clock,
                              MeterRegistry meters) {
        this.limits = limits;
        this.ips = ips;
        this.store = store;
        this.clock = clock;
        this.meters = meters;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = pathHelper.getPathWithinApplication(request);
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !PATHS.match(PATTERN, path)) {
            chain.doFilter(request, response);
            return;
        }
        Map<String, String> variables = PATHS.extractUriTemplateVariables(PATTERN, path);
        String addressKey = "claim-fail:ip:" + ips.resolve(request);
        String restaurantKey = "claim-fail:restaurant:" + variables.get("identifier").toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        Duration window = limits.getWindow();

        long wait = Math.max(blockedFor(addressKey, limits.getMaxFailuresPerAddress(), now, window),
                blockedFor(restaurantKey, limits.getMaxFailuresPerRestaurant(), now, window));
        if (wait > 0) {
            meters.counter("ratelimit.rejected", "endpoint", "counter-claim").increment();
            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(wait));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"urn:readybell:rate-limit:exceeded\",\"title\":\"Too Many Requests\","
                    + "\"status\":429,\"detail\":\"Too many attempts, retry in " + wait + " seconds.\"}");
            return;
        }

        chain.doFilter(request, response);

        int status = response.getStatus();
        if (status == 404 || status == 400) {          // not found / unavailable, or a malformed number
            meters.counter("claim.failures").increment();
            store.add(addressKey, now, window);
            store.add(restaurantKey, now, window);
        }
    }

    /** Seconds until the key may try again, or 0 when it is under its limit. */
    private long blockedFor(String key, int maxFailures, Instant now, Duration window) {
        var current = store.window(key, now, window);
        if (current.count() < maxFailures) {
            return 0;
        }
        return current.oldest().map(o -> Duration.between(now, o.plus(window)).toSeconds() + 1).orElse(1L);
    }
}

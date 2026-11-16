package org.readybell.platform.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** {@code readybell.rate-limit.*}: per-IP limits for the public endpoints. */
@ConfigurationProperties(prefix = "readybell.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    /** Share the counters between replicas through Redis. */
    private boolean redis = false;
    /** Proxies (IP or CIDR) whose {@code X-Forwarded-For} header is believed; empty means it is ignored. */
    private List<String> trustedProxies = new ArrayList<>();
    private List<Rule> rules = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isRedis() { return redis; }
    public void setRedis(boolean redis) { this.redis = redis; }
    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> trustedProxies) { this.trustedProxies = trustedProxies; }
    public List<Rule> getRules() { return rules; }
    public void setRules(List<Rule> rules) { this.rules = rules; }

    /** What a rule counts requests per. */
    public enum KeyBy {
        /** The client address (default). */
        IP,
        /**
         * The customer session in {@code /api/sessions/{id}/...}. Customers of one restaurant share a Wi-Fi, so an
         * address limit would throttle them for each other; a session's own polling is what is worth bounding.
         */
        SESSION,
        /** One bucket for everybody: a cap on an endpoint that creates rows and has no legitimate high-volume use. */
        GLOBAL
    }

    /**
     * At most {@code limit} requests per {@code window} per {@code keyBy} (client IP by default) to the matching endpoint.
     * Every rule that matches a request is applied, so a per-session rule and a looser per-address rule can sit side by side.
     */
    public static class Rule {
        /** Metric tag and counter key, e.g. {@code session-start}. */
        private String name;
        /** HTTP method, or empty for any. */
        private String method = "";
        /** Ant path pattern, e.g. {@code /api/sessions/*}{@code /subscriptions}. */
        private String path;
        private KeyBy keyBy = KeyBy.IP;
        private int limit = 60;
        private Duration window = Duration.ofMinutes(1);

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public KeyBy getKeyBy() { return keyBy; }
        public void setKeyBy(KeyBy keyBy) { this.keyBy = keyBy; }
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
    }
}

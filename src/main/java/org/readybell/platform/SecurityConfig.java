package org.readybell.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.readybell.restaurant.domain.Restaurant;
import org.readybell.restaurant.domain.RestaurantRepository;
import org.readybell.restaurant.domain.StaffAccountRepository;
import org.readybell.shared.domain.TenantId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.readybell.platform.auth.JwtProperties;
import org.readybell.platform.auth.JwtService;
import org.readybell.platform.auth.LoginAttemptLimiter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.readybell.platform.auth.FailureStore;
import org.readybell.platform.auth.RedisFailureStore;
import org.readybell.platform.auth.InMemoryFailureStore;
import org.readybell.platform.auth.InMemoryStreamTicketStore;
import org.readybell.platform.auth.RedisStreamTicketStore;
import org.readybell.platform.auth.StreamTicketStore;

/**
 * Spring Security configuration for the ReadyBell REST API (Req 7.1, 7.2, 8.1, 8.2, 9.1).
 *
 * <h2>Access rules</h2>
 * <ul>
 *   <li>{@code /api/sessions/**} and {@code /actuator/**} — public (no authentication)</li>
 *   <li>{@code /api/admin/**} — requires {@code RESTAURANT_ADMIN} (Req 8.1); {@code /api/orders/**} —
 *       requires authentication (Req 7.1); tenant scoping is enforced downstream by the tenant-resolution filter and the
 *       repository query layer.</li>
 *   <li>{@code /api/platform/**} — require {@code PLATFORM_ADMIN} role (Req 9.1, 9.4).</li>
 * </ul>
 *
 * <h2>Authentication mechanism</h2>
 * The dashboard signs in at {@code /api/login} and sends the HS256 JWT as a Bearer token; HTTP Basic is kept for
 * curl and tests. The API is stateless: no HTTP session is ever created, so any replica can serve any request.
 * CSRF is disabled because credentials travel in the {@code Authorization} header, never in a cookie.
 *
 * <h2>Users</h2>
 * Users come from {@code readybell.security.users} ({@link SecurityProperties}); there are no
 * built-in credentials. Each user is bound to a tenant, which the
 * {@link TenantResolutionFilter} uses as the only source of the tenant for authenticated calls.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({SecurityProperties.class, JwtProperties.class})
public class SecurityConfig {

    /**
     * Configures the main {@link SecurityFilterChain}.
     *
     * <p>Rules are evaluated top-to-bottom; the first matching rule wins.
     *
     * @param http the {@link HttpSecurity} builder provided by Spring
     * @return the configured {@link SecurityFilterChain} bean
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, org.readybell.platform.auth.TokenGuard tokenGuard, StreamTicketStore ticketStore,
            @Value("${readybell.cors.allowed-origins:}") List<String> corsOrigins,
            @Value("${readybell.cors.allowed-origin-patterns:}") List<String> corsOriginPatterns)
            throws Exception {
        http
            // Disable CSRF — REST API, credentials are in a header, not a cookie
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless by declaration, not just by default: never create an HttpSession (replicas share nothing)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // The UI is normally served from the same origin; CORS is opt-in for a separately hosted UI
            .cors(cors -> cors.configurationSource(request -> {
                var config = new CorsConfiguration();
                config.setAllowedOrigins(corsOrigins.stream().filter(o -> !o.isBlank()).toList());
                config.setAllowedOriginPatterns(corsOriginPatterns.stream().filter(o -> !o.isBlank()).toList());
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tenant-Id"));
                config.setMaxAge(3600L);
                return config;
            }))

            // Browser hardening: the built UI loads only its own scripts, plus Google Fonts
            .headers(headers -> headers
                // The tracking URL /track/{session} is the customer's credential: never hand it to other sites in Referer
                .referrerPolicy(referrer -> referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                // Nothing here needs the camera, microphone, location or payment APIs
                .permissionsPolicyHeader(permissions -> permissions.policy(
                        "camera=(), microphone=(), geolocation=(), payment=(), usb=(), serial=(), bluetooth=()"))
                // Spring sends HSTS only on requests it sees as secure; behind a proxy that ends TLS they arrive as http, so
                // always send it. Browsers ignore it over plain http, which keeps local development unaffected.
                .httpStrictTransportSecurity(hsts -> hsts.requestMatcher(
                        org.springframework.security.web.util.matcher.AnyRequestMatcher.INSTANCE)
                        .maxAgeInSeconds(15_552_000))
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                    + "font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; "
                    + "worker-src 'self'; manifest-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'")))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public: customer session endpoints and health/metrics probes
                .requestMatchers("/api/sessions/**").permitAll()
                .requestMatchers("/api/push/config").permitAll()
                .requestMatchers("/api/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                // Probes are public; metrics and the rest of actuator are for the platform operator
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("PLATFORM_ADMIN")

                // Platform admin: require PLATFORM_ADMIN role (Req 9.1, 9.4)
                .requestMatchers("/api/platform/**").hasRole("PLATFORM_ADMIN")

                // Restaurant admin and order management: require authentication (Req 7.1, 8.1)
                .requestMatchers("/api/admin/**").hasRole("RESTAURANT_ADMIN")
                .requestMatchers("/api/orders/**").authenticated()

                // The single-page UI (index.html, assets, service worker, deep links like /track/...) is public;
                // everything under /api not matched above requires authentication
                .requestMatchers(request -> !request.getRequestURI().startsWith("/api/")).permitAll()
                .anyRequest().authenticated()
            )

            // Bearer JWT for the dashboard (issued by /api/login); HTTP Basic kept for curl/tests
            .oauth2ResourceServer(oauth -> oauth
                    .bearerTokenResolver(bearerTokenResolver(ticketStore))
                    .jwt(jwt -> jwt.decoder(tokenGuard::decode)
                            .jwtAuthenticationConverter(SecurityConfig::toAuthentication)))
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    /**
     * Bearer token resolution.
     * <ul>
     *   <li>Skipped on the token endpoints so an expired token sent to {@code /api/auth/refresh}
     *       reaches the controller instead of being rejected as 401 up front.</li>
     *   <li>The browser {@code EventSource} cannot set headers, so {@code GET /api/orders/stream}
     *       (and only it) accepts a single-use {@code ticket} query parameter, obtained from
     *       {@code POST /api/orders/stream/ticket}. The ticket is consumed on first use and expires after
     *       30 s, so it is harmless in logs; the JWT itself is never accepted in a URL.</li>
     * </ul>
     */
    private static BearerTokenResolver bearerTokenResolver(StreamTicketStore ticketStore) {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String path = request.getRequestURI();
            if ("/api/auth/refresh".equals(path) || "/api/auth/logout".equals(path) || "/api/login".equals(path)) {
                return null;
            }
            String token = delegate.resolve(request);
            if (token == null && "GET".equals(request.getMethod()) && "/api/orders/stream".equals(path)) {
                String ticket = request.getParameter("ticket");
                return ticket == null || ticket.isBlank() ? null : ticketStore.consume(ticket).orElse(null);
            }
            return token;
        };
    }

    private static AbstractAuthenticationToken toAuthentication(Jwt jwt) {
        TenantUser principal = JwtService.toPrincipal(jwt);
        return new UsernamePasswordAuthenticationToken(principal, jwt, principal.getAuthorities());
    }

    @Bean
    public LoginAttemptLimiter loginAttemptLimiter(JwtProperties props, Clock clock,
            ObjectProvider<StringRedisTemplate> redis) {
        var login = props.getLogin();
        // With several replicas the failures must be counted in one place: Redis
        var redisTemplate = login.isRedis() ? redis.getIfAvailable() : null;
        FailureStore store = redisTemplate != null
                ? new RedisFailureStore(redisTemplate, "readybell:login-failures:")
                : new InMemoryFailureStore();
        return new LoginAttemptLimiter(login.getMaxFailures(), login.getMaxFailuresPerAddress(),
                login.getMaxFailuresPerAccount(), login.getWindow(), clock, store);
    }

    /** Optional network restriction for the platform operator's sign-in. */
    @Bean
    public org.readybell.platform.auth.OperatorNetworkPolicy operatorNetworkPolicy(
            @Value("${readybell.security.platform-admin-allowed-networks:}") List<String> networks) {
        return new org.readybell.platform.auth.OperatorNetworkPolicy(networks);
    }

    @Bean
    public StreamTicketStore streamTicketStore(Clock clock, ObjectProvider<StringRedisTemplate> redis,
            @Value("${readybell.sse.redis.enabled:false}") boolean redisEnabled) {
        var redisTemplate = redisEnabled ? redis.getIfAvailable() : null;
        return redisTemplate != null
                ? new RedisStreamTicketStore(redisTemplate, "readybell:stream-ticket:", clock)
                : new InMemoryStreamTicketStore(clock);
    }

    /** Where revoked tokens are remembered; Redis (when enabled) shares it between replicas. */
    @Bean
    public org.readybell.platform.auth.TokenRevocationStore tokenRevocationStore(Clock clock,
            ObjectProvider<StringRedisTemplate> redis, @Value("${readybell.sse.redis.enabled:false}") boolean redisEnabled) {
        var redisTemplate = redisEnabled ? redis.getIfAvailable() : null;
        return redisTemplate != null
                ? new org.readybell.platform.auth.RedisTokenRevocationStore(redisTemplate, "readybell:revoked-token:", clock)
                : new org.readybell.platform.auth.InMemoryTokenRevocationStore(clock);
    }

    @Bean
    public org.readybell.platform.auth.TokenSessions tokenSessions(org.readybell.platform.auth.TokenRevocationStore store,
            Clock clock, JwtProperties props) {
        // A revocation must outlive the longest time a token of the chain could still be used or refreshed
        return new org.readybell.platform.auth.TokenSessions(store, clock, props.getTtl().plus(props.getRefreshGrace()).plusHours(1));
    }

    @Bean
    public org.readybell.platform.auth.TokenGuard tokenGuard(JwtService jwtService,
            org.readybell.platform.auth.TokenSessions sessions, UserDetailsService users, Clock clock,
            @Value("${readybell.security.account-check-cache:30s}") java.time.Duration accountCheckCache) {
        return new org.readybell.platform.auth.TokenGuard(jwtService, sessions,
                new org.readybell.platform.auth.AccountGuard(users, clock, accountCheckCache));
    }

    @Bean
    public JwtService jwtService(JwtProperties props, Clock clock) {
        return new JwtService(props, clock);
    }

    /**
     * User lookup used by login and token refresh: users configured in {@link SecurityProperties} (platform
     * admins, dev users) first, then the staff accounts stored in the database.
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder, SecurityProperties props,
                                                 StaffAccountRepository staffAccounts,
                                                 RestaurantRepository restaurants) {
        // InMemoryUserDetailsManager would return a plain User and drop the tenant, so keep our own map.
        // Configured users (platform admins, dev users) win over database accounts of the same name.
        Map<String, TenantUser> configured = props.getUsers().stream()
                .collect(Collectors.toUnmodifiableMap(
                        u -> u.getUsername().toLowerCase(Locale.ROOT),
                        u -> new TenantUser(
                                u.getUsername(),
                                passwordHash(encoder, u.getPassword()),
                                u.getRoles().stream()
                                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                        .toList(),
                                u.getTenantId() == null ? null : new TenantId(u.getTenantId()))));
        return username -> {
            String key = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
            TenantUser user = configured.get(key);
            if (user != null) {
                // Fresh instance per lookup: Spring Security erases credentials on the returned object.
                return new TenantUser(user.getUsername(), user.getPassword(), user.getAuthorities(), user.getTenantId());
            }
            return staffAccounts.findByUsername(key)
                    .map(account -> {
                        boolean restaurantActive = restaurants.findByTenantId(account.getTenantId())
                                .map(Restaurant::isActive).orElse(false);
                        return new TenantUser(
                                account.getUsername(),
                                account.getPasswordHash() == null ? "" : account.getPasswordHash(),
                                account.canSignIn() && restaurantActive,
                                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())),
                                account.getTenantId());
                    })
                    .orElseThrow(() -> new UsernameNotFoundException(key));
        };
    }

    /** Configured passwords are plain text (dev) or pre-hashed with the {@code {bcrypt}} prefix (production). */
    private static String passwordHash(PasswordEncoder encoder, String configured) {
        return configured.startsWith("{bcrypt}") ? configured.substring("{bcrypt}".length()) : encoder.encode(configured);
    }

    /**
     * Password encoder using BCrypt.
     *
     * @return a {@link BCryptPasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

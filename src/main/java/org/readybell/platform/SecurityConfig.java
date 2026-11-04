package org.readybell.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
 * HTTP Basic auth is used for the MVP (full JWT integration is deferred to a later task).
 * CSRF is disabled because the API is stateless REST (no browser form submissions).
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
            @org.springframework.beans.factory.annotation.Value("${readybell.cors.allowed-origins:}") List<String> corsOrigins,
            @org.springframework.beans.factory.annotation.Value("${readybell.cors.allowed-origin-patterns:}") List<String> corsOriginPatterns)
            throws Exception {
        http
            // Disable CSRF — REST API, no browser form submissions
            .csrf(AbstractHttpConfigurer::disable)

            // The UI is normally served from the same origin; CORS is opt-in for a separately hosted UI
            .cors(cors -> cors.configurationSource(request -> {
                var config = new org.springframework.web.cors.CorsConfiguration();
                config.setAllowedOrigins(corsOrigins.stream().filter(o -> !o.isBlank()).toList());
                config.setAllowedOriginPatterns(corsOriginPatterns.stream().filter(o -> !o.isBlank()).toList());
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tenant-Id"));
                config.setMaxAge(3600L);
                return config;
            }))

            // Browser hardening: the built UI loads only its own scripts, plus Google Fonts
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                    + "font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self'; "
                    + "worker-src 'self'; manifest-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'")))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public: customer session endpoints and health/metrics probes
                .requestMatchers("/api/sessions/**").permitAll()
                .requestMatchers("/api/push/config").permitAll()
                .requestMatchers("/api/login", "/api/auth/refresh").permitAll()
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
                    .bearerTokenResolver(bearerTokenResolver())
                    .jwt(jwt -> jwt.decoder(jwtService::decode)
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
     *       (and only it) also accepts the token as a {@code token} query parameter. Such URLs can end
     *       up in access logs, so keep proxy/server logs from recording query strings.</li>
     * </ul>
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String path = request.getRequestURI();
            if ("/api/auth/refresh".equals(path) || "/api/login".equals(path)) {
                return null;
            }
            String token = delegate.resolve(request);
            if (token == null && "GET".equals(request.getMethod()) && "/api/orders/stream".equals(path)) {
                String param = request.getParameter("token");
                return param == null || param.isBlank() ? null : param;
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
            org.springframework.beans.factory.ObjectProvider<org.springframework.data.redis.core.StringRedisTemplate> redis) {
        var login = props.getLogin();
        // With several replicas the failures must be counted in one place: Redis
        var redisTemplate = login.isRedis() ? redis.getIfAvailable() : null;
        org.readybell.platform.auth.FailureStore store = redisTemplate != null
                ? new org.readybell.platform.auth.RedisFailureStore(redisTemplate, "readybell:login-failures:")
                : new org.readybell.platform.auth.InMemoryFailureStore();
        return new LoginAttemptLimiter(login.getMaxFailures(), login.getWindow(), clock, store);
    }

    @Bean
    public JwtService jwtService(JwtProperties props, Clock clock) {
        return new JwtService(props, clock);
    }

    /**
     * In-memory user store built from {@link SecurityProperties}.
     *
     * <p>Replace with a database-backed {@link UserDetailsService} and JWT authentication when
     * full auth wiring is done (task 21).
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

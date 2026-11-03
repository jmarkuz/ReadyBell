package org.readybell.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring Security configuration for the ReadyBell REST API (Req 7.1, 7.2, 8.1, 8.2, 9.1).
 *
 * <h2>Access rules</h2>
 * <ul>
 *   <li>{@code /api/sessions/**} and {@code /actuator/**} — public (no authentication)</li>
 *   <li>{@code /api/admin/**} and {@code /api/orders/**} — require authentication (Req 8.1,
 *       7.1); tenant scoping is enforced downstream by the tenant-resolution filter and the
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
            // Disable CSRF — REST API, no browser form submissions
            .csrf(AbstractHttpConfigurer::disable)

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public: customer session endpoints and health/metrics probes
                .requestMatchers("/api/sessions/**").permitAll()
                .requestMatchers("/api/login", "/api/auth/refresh").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                // Platform admin: require PLATFORM_ADMIN role (Req 9.1, 9.4)
                .requestMatchers("/api/platform/**").hasRole("PLATFORM_ADMIN")

                // Restaurant admin and order management: require authentication (Req 7.1, 8.1)
                .requestMatchers("/api/admin/**").authenticated()
                .requestMatchers("/api/orders/**").authenticated()

                // All other requests require authentication by default
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
    public LoginAttemptLimiter loginAttemptLimiter(JwtProperties props, Clock clock) {
        return new LoginAttemptLimiter(props.getLogin().getMaxFailures(), props.getLogin().getWindow(), clock);
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
    public UserDetailsService userDetailsService(PasswordEncoder encoder, SecurityProperties props) {
        // InMemoryUserDetailsManager would return a plain User and drop the tenant, so keep our own map.
        Map<String, TenantUser> users = props.getUsers().stream()
                .collect(Collectors.toUnmodifiableMap(
                        SecurityProperties.User::getUsername,
                        u -> new TenantUser(
                                u.getUsername(),
                                encoder.encode(u.getPassword()),
                                u.getRoles().stream()
                                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                        .toList(),
                                u.getTenantId() == null ? null : new TenantId(u.getTenantId()))));
        return username -> {
            TenantUser user = users.get(username);
            if (user == null) {
                throw new UsernameNotFoundException(username);
            }
            // Fresh instance per lookup: Spring Security erases credentials on the returned object.
            return new TenantUser(user.getUsername(), user.getPassword(), user.getAuthorities(), user.getTenantId());
        };
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

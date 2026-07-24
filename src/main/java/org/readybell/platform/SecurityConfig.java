package org.readybell.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

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
 * <h2>In-memory user (testing only)</h2>
 * A default in-memory user {@code admin / admin} with role {@code RESTAURANT_ADMIN} is
 * registered so manual and integration testing can exercise the protected endpoints without
 * an external identity provider.
 */
@Configuration
@EnableWebSecurity
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — REST API, no browser form submissions
            .csrf(AbstractHttpConfigurer::disable)

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Public: customer session endpoints and health/metrics probes
                .requestMatchers("/api/sessions/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                // Platform admin: require PLATFORM_ADMIN role (Req 9.1, 9.4)
                .requestMatchers("/api/platform/**").hasRole("PLATFORM_ADMIN")

                // Restaurant admin and order management: require authentication (Req 7.1, 8.1)
                .requestMatchers("/api/admin/**").authenticated()
                .requestMatchers("/api/orders/**").authenticated()

                // All other requests require authentication by default
                .anyRequest().authenticated()
            )

            // HTTP Basic auth for MVP (Req 7.1, 8.1; JWT deferred to task 21)
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    /**
     * In-memory user store for testing.
     *
     * <p>Provides a single user {@code admin / admin} with the {@code RESTAURANT_ADMIN}
     * role so that the protected endpoints can be exercised without a real identity provider.
     * Replace this with a database-backed {@link UserDetailsService} and JWT authentication
     * when full auth wiring is done in task 21.
     *
     * @param encoder the {@link PasswordEncoder} used to hash the test password
     * @return an {@link InMemoryUserDetailsManager} with the test user
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var admin = User.builder()
                .username("admin")
                .password(encoder.encode("admin"))
                .roles("RESTAURANT_ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
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

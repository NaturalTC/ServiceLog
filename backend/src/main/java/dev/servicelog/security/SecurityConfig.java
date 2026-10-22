package dev.servicelog.security;

import dev.servicelog.sync.IdempotencyFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Security rules for every request:
// - Stateless: no server sessions; the JWT proves identity on each request
// - CSRF off: tokens travel in the Authorization header, not cookies, so CSRF doesn't apply
// - Only login, email-link (verify, reset, unsubscribe) and health endpoints are public;
//   everything else needs a valid token
// - No CORS config: the frontend is served from the same origin, so cross-origin calls are refused
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JdbcTemplate jdbc;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, JdbcTemplate jdbc) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.jdbc = jdbc;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/unsubscribe").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Spring's error page (e.g. a 404 for an unknown path). The original request was already
                        // checked; without this, a logged-in 404 came back as 401 and the app logged the owner out
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Photo files: signed, expiring links instead of the login token (see PhotoLinks)
                        .requestMatchers(HttpMethod.GET, "/api/photos/*/full", "/api/photos/*/thumb").permitAll()
                        .anyRequest().authenticated())
                // Answer 401 (not logged in) instead of Spring's default 403 for missing tokens
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Repeated sends from the offline queue (S21); after the token check, so it knows the user.
                // Not a bean, so it only runs here, inside the security chain.
                .addFilterAfter(new IdempotencyFilter(jdbc), JwtAuthFilter.class);

        return http.build();
    }

    // BCrypt salts and hashes passwords so they can't be reversed if the database leaks
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

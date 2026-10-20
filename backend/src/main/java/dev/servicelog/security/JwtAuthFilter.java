package dev.servicelog.security;

import dev.servicelog.auth.AppUser;
import dev.servicelog.auth.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Runs once per request before the controllers. If the request carries a valid
// "Authorization: Bearer <token>" header, it marks the request as authenticated.
// It never rejects requests itself: SecurityConfig decides which routes need a login.
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<SimpleGrantedAuthority> OWNER = List.of(new SimpleGrantedAuthority("ROLE_OWNER"));

    private final JwtService jwtService;
    private final AppUserRepository users;

    public JwtAuthFilter(JwtService jwtService, AppUserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            jwtService.validate(token).ifPresent(claims -> authenticate(claims, request));
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(JwtService.TokenClaims claims, HttpServletRequest request) {
        // Load the account: the token stops working if the account was removed,
        // or if the password changed since the token was issued
        users.findByEmailIgnoreCase(claims.email())
                .filter(user -> user.getSessionVersion() == claims.sessionVersion())
                .map(AppUser::getEmail)
                .ifPresent(email -> {
                    var authentication = new UsernamePasswordAuthenticationToken(email, null, OWNER);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
    }
}

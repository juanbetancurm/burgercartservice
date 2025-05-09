package com.rockburger.cartservice.configuration.security;

import com.rockburger.cartservice.domain.model.CartUserModel;
import com.rockburger.cartservice.domain.spi.ICartJwtPersistencePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@Component
@Order(1)
public class JwtCartAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtCartAuthenticationFilter.class);

    private final ICartJwtPersistencePort cartJwtPersistencePort;

    public JwtCartAuthenticationFilter(ICartJwtPersistencePort cartJwtPersistencePort) {
        this.cartJwtPersistencePort = cartJwtPersistencePort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            // Enhanced logging for troubleshooting
            logger.debug("Processing cart service request to: {} with JWT: {}",
                    request.getRequestURI(), jwt != null ? "present" : "not present");

            if (jwt != null && cartJwtPersistencePort.isTokenValid(jwt)) {
                CartUserModel user = cartJwtPersistencePort.validateToken(jwt);

                // Normalize role format for Spring Security
                String role = user.getRole();
                if (role != null && !role.startsWith("ROLE_")) {
                    role = "ROLE_" + role;
                }

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority(role)
                );

                // Store user details including email as principal
                String email = user.getEmail();
                logger.debug("Authenticated user email: {}", email);

                // Store JWT token as credentials and email as principal
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                email, // Use email as principal
                                jwt,   // Store token as credentials
                                authorities
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Store userId in request attribute for easy access by controllers
                request.setAttribute("userId", email);

                logger.debug("User authenticated in cart service with role: {}", role);
            } else if (jwt != null) {
                logger.warn("Invalid JWT token received by cart service");
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication in cart service: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
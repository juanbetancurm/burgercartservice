package com.rockburger.cartservice.configuration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Enable credentials for JWT authentication
        configuration.setAllowCredentials(true);

        // Specify exact frontend origins and main service
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173",  // Vite development server
                "http://localhost:3000",  // Alternative React dev server
                "http://localhost:8080",  // Alternative frontend port
                "http://localhost:8090"   // Main service (for Feign calls)
                // Add production URLs here:
                // "https://your-production-domain.com"
        ));

        // Allow all headers
        configuration.addAllowedHeader("*");
        
        // Explicitly expose Authorization header for frontend access
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"
        ));

        // Allow specific HTTP methods including OPTIONS for preflight
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // Configure how long preflight requests can be cached
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public CorsFilter corsFilter() {
        return new CorsFilter(corsConfigurationSource());
    }
}

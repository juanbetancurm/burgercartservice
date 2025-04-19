package com.rockburger.cartservice.configuration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfigCart {
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfigCart.class);

    private final JwtCartAuthenticationFilter jwtCartAuthenticationFilter;

    public SecurityConfigCart(JwtCartAuthenticationFilter jwtCartAuthenticationFilter) {
        this.jwtCartAuthenticationFilter = jwtCartAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        logger.info("Configuring cart service security");

        http
                .cors().and()
                .csrf().disable()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/error").permitAll()
                .antMatchers("/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-resources/**",
                        "/actuator/health",
                        "/webjars/**").permitAll()
                // Ensure both client and auxiliar roles can access cart endpoints
                .antMatchers("/cart/**").hasAnyRole("client", "auxiliar")
                .anyRequest().authenticated()
                .and()
                .addFilterBefore(jwtCartAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
package com.rockburger.cartservice.configuration.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CartFilterConfig {

    @Bean
    public FilterRegistrationBean<JwtCartAuthenticationFilter> jwtCartFilterRegistration(
            JwtCartAuthenticationFilter filter) {
        FilterRegistrationBean<JwtCartAuthenticationFilter> registrationBean =
                new FilterRegistrationBean<>(filter);
        registrationBean.setEnabled(false); // Disable duplicate registration
        return registrationBean;
    }
}
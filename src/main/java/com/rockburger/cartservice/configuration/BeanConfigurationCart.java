package com.rockburger.cartservice.configuration;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.adapter.CartAdapter;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.adapter.CartJwtAdapter;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper.ICartEntityMapper;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper.ICartItemEntityMapper;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.repository.ICartItemRepository;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.repository.ICartRepository;
import com.rockburger.cartservice.adapters.driving.http.dto.response.CartResponse;
import com.rockburger.cartservice.adapters.driving.http.mapper.ICartResponseMapper;
import com.rockburger.cartservice.configuration.security.JwtCartKeyProvider;
import com.rockburger.cartservice.domain.api.ICartServicePort;
import com.rockburger.cartservice.domain.api.usecase.CartUseCase;
import com.rockburger.cartservice.domain.model.CartModel;
import com.rockburger.cartservice.domain.spi.ICartJwtPersistencePort;
import com.rockburger.cartservice.domain.spi.ICartPersistencePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class BeanConfigurationCart {
    @Value("${jwt.secret}")
    private String jwtSecret;

    // Persistence beans
    @Bean
    public ICartPersistencePort cartPersistencePort(
            ICartRepository cartRepository,
            ICartEntityMapper cartEntityMapper,
            ICartItemRepository cartItemRepository,
            ICartItemEntityMapper cartItemEntityMapper) {
        return new CartAdapter(cartRepository, cartEntityMapper, cartItemRepository, cartItemEntityMapper);
    }

    // Service beans
    @Bean
    public ICartServicePort cartServicePort(ICartPersistencePort cartPersistencePort) {
        return new CartUseCase(cartPersistencePort);
    }

    // JWT beans
    @Bean
    public ICartJwtPersistencePort cartJwtPersistencePort(JwtCartKeyProvider jwtCartKeyProvider) {
        return new CartJwtAdapter(jwtCartKeyProvider, jwtSecret);
    }

    // Mapper beans
    @Bean
    public ICartResponseMapper cartResponseMapper() {
        return model -> {
            if (model == null) {
                return null;
            }
            CartResponse response = new CartResponse();
            response.setId(model.getId());
            response.setUserId(model.getUserId());
            response.setTotal(model.getTotal());
            response.setLastUpdated(model.getLastUpdated());
            response.setStatus(model.getStatus());
            return response;
        };
    }
}
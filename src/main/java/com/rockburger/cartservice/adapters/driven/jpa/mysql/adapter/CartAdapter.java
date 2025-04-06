package com.rockburger.cartservice.adapters.driven.jpa.mysql.adapter;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartEntity;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper.ICartEntityMapper;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.repository.ICartRepository;
import com.rockburger.cartservice.domain.exception.ConcurrentModificationException;
import com.rockburger.cartservice.domain.model.CartModel;
import com.rockburger.cartservice.domain.spi.ICartPersistencePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CartAdapter implements ICartPersistencePort {
    private static final Logger logger = LoggerFactory.getLogger(CartAdapter.class);

    private final ICartRepository cartRepository;
    private final ICartEntityMapper cartEntityMapper;

    public CartAdapter(ICartRepository cartRepository, ICartEntityMapper cartEntityMapper) {
        this.cartRepository = cartRepository;
        this.cartEntityMapper = cartEntityMapper;
    }

    @Override
    @Transactional
    public CartModel save(CartModel cartModel) {
        try {
            logger.debug("Saving cart for user: {}", cartModel.getUserId());
            CartEntity cartEntity = cartEntityMapper.toEntity(cartModel);
            CartEntity savedEntity = cartRepository.save(cartEntity);
            return cartEntityMapper.toModel(savedEntity);
        } catch (ObjectOptimisticLockingFailureException e) {
            logger.error("Concurrent modification detected when saving cart", e);
            throw new ConcurrentModificationException("Cart was modified concurrently");
        } catch (Exception e) {
            logger.error("Error saving cart: ", e);
            throw new RuntimeException("Failed to save cart", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CartModel> findByUserIdAndStatus(String userId, String status) {
        logger.debug("Finding cart for user: {} with status: {}", userId, status);
        return cartRepository.findByUserIdAndStatus(userId, status)
                .map(cartEntityMapper::toModel);
    }

    @Override
    @Transactional
    public void deleteByUserId(String userId) {
        logger.debug("Deleting cart for user: {}", userId);
        cartRepository.findByUserIdAndStatus(userId, "ACTIVE")
                .ifPresent(cartRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserIdAndStatus(String userId, String status) {
        return cartRepository.existsByUserIdAndStatus(userId, status);
    }

    @Override
    @Transactional
    public void updateCartStatus(String userId, String oldStatus, String newStatus) {
        logger.debug("Updating cart status for user: {} from {} to {}", userId, oldStatus, newStatus);
        int updatedCount = cartRepository.updateCartStatus(userId, oldStatus, newStatus);
        if (updatedCount == 0) {
            logger.warn("No cart found to update status for user: {}", userId);
        }
    }

    @Override
    @Transactional
    public void deleteExpiredCarts(int expirationHours) {
        logger.info("Deleting expired carts older than {} hours", expirationHours);
        LocalDateTime expirationTime = LocalDateTime.now().minusHours(expirationHours);
        cartRepository.deleteExpiredCarts(expirationTime);
    }
}

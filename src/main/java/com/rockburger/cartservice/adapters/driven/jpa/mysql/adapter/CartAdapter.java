package com.rockburger.cartservice.adapters.driven.jpa.mysql.adapter;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartEntity;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartItemEntity;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper.ICartEntityMapper;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper.ICartItemEntityMapper;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.repository.ICartItemRepository;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.repository.ICartRepository;
import com.rockburger.cartservice.domain.exception.CartNotFoundException;
import com.rockburger.cartservice.domain.exception.ConcurrentModificationException;
import com.rockburger.cartservice.domain.model.CartModel;
import com.rockburger.cartservice.domain.spi.ICartPersistencePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CartAdapter implements ICartPersistencePort {
    private static final Logger logger = LoggerFactory.getLogger(CartAdapter.class);

    private final ICartRepository cartRepository;
    private final ICartItemRepository cartItemRepository;
    private final ICartEntityMapper cartEntityMapper;
    private final ICartItemEntityMapper cartItemEntityMapper;

    public CartAdapter(
            ICartRepository cartRepository,
            ICartEntityMapper cartEntityMapper,
            ICartItemRepository cartItemRepository,
            ICartItemEntityMapper cartItemEntityMapper) {
        this.cartRepository = cartRepository;
        this.cartEntityMapper = cartEntityMapper;
        this.cartItemRepository = cartItemRepository;
        this.cartItemEntityMapper = cartItemEntityMapper;
    }

    @Override
    @Transactional
    public CartModel save(CartModel cartModel) {
        try {
            logger.debug("Saving cart for user: {}", cartModel.getUserId());

            CartEntity cartEntity;
            boolean isNewCart = cartModel.getId() == null;

            if (isNewCart) {
                // For new carts
                cartEntity = cartEntityMapper.toEntity(cartModel);
                cartEntity = cartRepository.save(cartEntity);
            } else {
                // For existing carts, first retrieve the current entity
                CartEntity existingCart = cartRepository.findById(cartModel.getId())
                        .orElseThrow(() -> new CartNotFoundException("Cart not found with ID: " + cartModel.getId()));

                // Clear existing items to avoid duplicates
                existingCart.getItems().clear();

                // Update fields from model
                cartEntityMapper.updateEntity(existingCart, cartModel);

                // Save the cart first
                cartEntity = cartRepository.save(existingCart);

                // Need to flush to ensure the cart is saved before adding items
                cartRepository.flush();
            }

            // Now handle the items separately
            if (cartModel.getItems() != null && !cartModel.getItems().isEmpty()) {
                for (var itemModel : cartModel.getItems()) {
                    CartItemEntity itemEntity = cartItemEntityMapper.toEntity(itemModel);
                    itemEntity.setCart(cartEntity);
                    cartItemRepository.save(itemEntity);
                }

                // Reload the cart with items
                cartEntity = cartRepository.findById(cartEntity.getId()).orElseThrow();
            }

            return cartEntityMapper.toModel(cartEntity);

        } catch (ObjectOptimisticLockingFailureException e) {
            logger.error("Concurrent modification detected when saving cart", e);
            throw new ConcurrentModificationException("Cart was modified concurrently");
        } catch (DataIntegrityViolationException e) {
            logger.error("Data integrity violation when saving cart", e);
            throw new RuntimeException("Failed to save cart due to data integrity violation", e);
        } catch (Exception e) {
            logger.error("Error saving cart: {}", e.getMessage(), e);
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
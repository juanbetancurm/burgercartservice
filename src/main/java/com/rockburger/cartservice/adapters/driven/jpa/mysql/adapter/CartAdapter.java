package com.rockburger.cartservice.adapters.driven.jpa.mysql.adapter;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartEntity;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartItemEntity;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper.ICartEntityMapper;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper.ICartItemEntityMapper;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.repository.ICartRepository;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.repository.ICartItemRepository;
import com.rockburger.cartservice.domain.exception.ConcurrentModificationException;
import com.rockburger.cartservice.domain.model.CartModel;
import com.rockburger.cartservice.domain.model.CartItemModel;
import com.rockburger.cartservice.domain.spi.ICartPersistencePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CartAdapter implements ICartPersistencePort {
    private static final Logger logger = LoggerFactory.getLogger(CartAdapter.class);

    private final ICartRepository cartRepository;
    private final ICartItemRepository cartItemRepository;
    private final ICartEntityMapper cartEntityMapper;
    private final ICartItemEntityMapper cartItemEntityMapper;

    public CartAdapter(ICartRepository cartRepository,
                       ICartItemRepository cartItemRepository,
                       ICartEntityMapper cartEntityMapper,
                       ICartItemEntityMapper cartItemEntityMapper) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartEntityMapper = cartEntityMapper;
        this.cartItemEntityMapper = cartItemEntityMapper;
    }

    @Override
    @Transactional
    public CartModel save(CartModel cartModel) {
        logger.debug("Saving cart for user: {}", cartModel.getUserId());

        try {
            CartEntity cartEntity;

            if (cartModel.getId() != null) {
                // Update existing cart
                cartEntity = cartRepository.findById(cartModel.getId())
                        .orElseThrow(() -> new RuntimeException("Cart not found with ID: " + cartModel.getId()));

                // Clear existing items first to avoid orphan issues
                cartEntity.getItems().clear();
                cartRepository.saveAndFlush(cartEntity);

                // Update cart properties
                cartEntity.setUserId(cartModel.getUserId());
                cartEntity.setTotal(cartModel.getTotal());
                cartEntity.setLastUpdated(cartModel.getLastUpdated());
                cartEntity.setStatus(cartModel.getStatus());

            } else {
                // Create new cart
                cartEntity = cartEntityMapper.toEntity(cartModel);
                cartEntity.setItems(new ArrayList<>());
            }

            // Save cart first to get the ID
            cartEntity = cartRepository.saveAndFlush(cartEntity);

            // Now add items with proper relationship management
            for (CartItemModel itemModel : cartModel.getItems()) {
                CartItemEntity itemEntity = cartItemEntityMapper.toEntity(itemModel);
                itemEntity.setCart(cartEntity);
                itemEntity.calculateSubtotal(); // Ensure subtotal is calculated
                cartEntity.addItem(itemEntity);
            }

            // Final save with items
            CartEntity savedEntity = cartRepository.saveAndFlush(cartEntity);

            CartModel savedModel = cartEntityMapper.toModel(savedEntity);
            logger.info("Successfully saved cart ID {} for user {} with {} items",
                    savedEntity.getId(), savedModel.getUserId(), savedModel.getItems().size());

            return savedModel;

        } catch (ObjectOptimisticLockingFailureException e) {
            logger.error("Optimistic locking failure when saving cart for user {}: {}", cartModel.getUserId(), e.getMessage());
            throw new ConcurrentModificationException("Cart was modified by another transaction", e);
        } catch (DataIntegrityViolationException e) {
            logger.error("Data integrity violation when saving cart for user {}: {}", cartModel.getUserId(), e.getMessage());
            throw new RuntimeException("Data integrity violation when saving cart", e);
        } catch (Exception e) {
            logger.error("Error saving cart for user {}: {}", cartModel.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Failed to save cart", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CartModel> findByUserIdAndStatus(String userId, String status) {
        logger.debug("Finding cart for user: {} with status: {}", userId, status);

        List<CartEntity> carts = cartRepository.findByUserIdAndStatus(userId, status);
        if (carts.isEmpty()) {
            return Optional.empty();
        }

        // Return the most recent cart (assuming the list is ordered or take the first one)
        CartEntity cartEntity = carts.get(0);
        return Optional.of(cartEntityMapper.toModel(cartEntity));
    }

    @Override
    @Transactional
    public void deleteByUserId(String userId) {
        logger.debug("Deleting cart for user: {}", userId);

        try {
            // Find and delete active carts for the user
            List<CartEntity> carts = cartRepository.findByUserIdAndStatus(userId, "ACTIVE");
            if (!carts.isEmpty()) {
                for (CartEntity cart : carts) {
                    cartRepository.delete(cart);
                }
                logger.info("Successfully deleted {} cart(s) for user: {}", carts.size(), userId);
            } else {
                logger.info("No active cart found to delete for user: {}", userId);
            }
        } catch (Exception e) {
            logger.error("Error deleting cart for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete cart", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserIdAndStatus(String userId, String status) {
        logger.debug("Checking if cart exists for user: {} with status: {}", userId, status);
        return cartRepository.existsByUserIdAndStatus(userId, status);
    }

    @Override
    @Transactional
    public void updateCartStatus(String userId, String oldStatus, String newStatus) {
        logger.debug("Updating cart status for user: {} from {} to {}", userId, oldStatus, newStatus);

        try {
            // Since the repository doesn't have updateCartStatus method, implement it manually
            List<CartEntity> carts = cartRepository.findByUserIdAndStatus(userId, oldStatus);
            int updatedCount = 0;

            for (CartEntity cart : carts) {
                cart.setStatus(newStatus);
                cartRepository.save(cart);
                updatedCount++;
            }

            if (updatedCount == 0) {
                logger.warn("No cart found to update status for user: {} with status: {}", userId, oldStatus);
            } else {
                logger.info("Successfully updated {} cart(s) status for user: {} from {} to {}",
                        updatedCount, userId, oldStatus, newStatus);
            }
        } catch (Exception e) {
            logger.error("Error updating cart status for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to update cart status", e);
        }
    }

    @Override
    @Transactional
    public void deleteExpiredCarts(int expirationHours) {
        logger.info("Deleting expired carts older than {} hours", expirationHours);

        try {
            // Since the repository doesn't have deleteExpiredCarts method, implement it manually
            // This is a simple implementation - you might want to add a custom query for better performance
            List<CartEntity> allCarts = cartRepository.findAll();
            java.time.LocalDateTime expirationTime = java.time.LocalDateTime.now().minusHours(expirationHours);

            int deletedCount = 0;
            for (CartEntity cart : allCarts) {
                if (cart.getLastUpdated().isBefore(expirationTime) && "ACTIVE".equals(cart.getStatus())) {
                    cartRepository.delete(cart);
                    deletedCount++;
                }
            }

            logger.info("Successfully deleted {} expired carts older than {}", deletedCount, expirationTime);
        } catch (Exception e) {
            logger.error("Error deleting expired carts: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete expired carts", e);
        }
    }
}
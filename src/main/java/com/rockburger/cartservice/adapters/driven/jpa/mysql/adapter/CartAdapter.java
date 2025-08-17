package com.rockburger.cartservice.adapters.driven.jpa.mysql.adapter;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartEntity;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper.ICartEntityMapper;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.repository.ICartRepository;
import com.rockburger.cartservice.domain.exception.CartNotFoundException;
import com.rockburger.cartservice.domain.exception.ConcurrentCartModificationException;
import com.rockburger.cartservice.domain.model.CartModel;
import com.rockburger.cartservice.domain.spi.ICartPersistencePort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CartAdapter implements ICartPersistencePort {
    private static final Logger logger = LoggerFactory.getLogger(CartAdapter.class);

    private final ICartRepository cartRepository;
    private final ICartEntityMapper cartEntityMapper;

    // Session management constants
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String ABANDONED_STATUS = "ABANDONED";
    private static final String COMPLETED_STATUS = "COMPLETED";
    private static final int CART_EXPIRY_HOURS = 24;

    public CartAdapter(ICartRepository cartRepository, ICartEntityMapper cartEntityMapper) {
        this.cartRepository = cartRepository;
        this.cartEntityMapper = cartEntityMapper;
    }

    @Override
    @Transactional
    public CartModel save(CartModel cartModel) {
        logger.debug("Saving cart for user: {}", cartModel.getUserId());

        try {
            // Validate cart before saving
            validateCartForSave(cartModel);

            CartEntity cartEntity = cartEntityMapper.toEntity(cartModel);

            // Handle optimistic locking
            CartEntity savedEntity;
            try {
                savedEntity = cartRepository.save(cartEntity);
                logger.debug("Successfully saved cart with ID: {}", savedEntity.getId());
            } catch (OptimisticLockingFailureException e) {
                logger.warn("Optimistic locking failure for cart user {}: {}", cartModel.getUserId(), e.getMessage());
                throw new ConcurrentCartModificationException("Cart was modified by another session. Please refresh and try again.");
            }

            CartModel savedModel = cartEntityMapper.toModel(savedEntity);

            // Log cart summary for debugging
            logger.debug("Saved cart summary: {}", savedModel.getSummary());

            return savedModel;

        } catch (ConcurrentCartModificationException e) {
            // Re-throw concurrency exceptions
            throw e;
        } catch (Exception e) {
            logger.error("Error saving cart for user {}: {}", cartModel.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Failed to save cart", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CartModel> findByUserIdAndStatus(String userId, String status) {
        logger.debug("Finding cart for user: {} with status: {}", userId, status);

        try {
            validateUserId(userId);
            validateStatus(status);

            List<CartEntity> cartEntities = cartRepository.findByUserIdAndStatus(userId, status);

            if (cartEntities.isEmpty()) {
                logger.debug("No cart found for user {} with status {}", userId, status);
                return Optional.empty();
            }

            // Handle multiple active carts (cleanup scenario)
            if (cartEntities.size() > 1) {
                logger.warn("Found {} carts for user {} with status {}. Cleaning up duplicates.",
                        cartEntities.size(), userId, status);
                return handleMultipleCarts(cartEntities, userId, status);
            }

            CartEntity cartEntity = cartEntities.get(0);
            CartModel cartModel = cartEntityMapper.toModel(cartEntity);

            // Check if cart is expired and handle accordingly
            if (cartModel.isExpired() && ACTIVE_STATUS.equals(status)) {
                logger.info("Found expired active cart for user {}, abandoning it", userId);
                abandonExpiredCart(cartEntity);
                return Optional.empty();
            }

            logger.debug("Found cart: {}", cartModel.getSummary());
            return Optional.of(cartModel);

        } catch (Exception e) {
            logger.error("Error finding cart for user {} with status {}: {}", userId, status, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void deleteByUserId(String userId) {
        logger.debug("Deleting carts for user: {}", userId);

        try {
            validateUserId(userId);

            // Find all carts for the user
            List<CartEntity> userCarts = cartRepository.findByUserId(userId);

            if (userCarts.isEmpty()) {
                logger.info("No carts found to delete for user: {}", userId);
                return;
            }

            // Log what we're about to delete
            userCarts.forEach(cart ->
                    logger.debug("Deleting cart ID {} with status {} for user {}",
                            cart.getId(), cart.getStatus(), userId));

            // Delete all carts for the user
            cartRepository.deleteAll(userCarts);

            logger.info("Successfully deleted {} cart(s) for user: {}", userCarts.size(), userId);

        } catch (Exception e) {
            logger.error("Error deleting carts for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete carts for user", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserIdAndStatus(String userId, String status) {
        logger.debug("Checking if cart exists for user: {} with status: {}", userId, status);

        try {
            validateUserId(userId);
            validateStatus(status);

            boolean exists = cartRepository.existsByUserIdAndStatus(userId, status);
            logger.debug("Cart exists for user {} with status {}: {}", userId, status, exists);

            return exists;
        } catch (Exception e) {
            logger.error("Error checking cart existence for user {} with status {}: {}",
                    userId, status, e.getMessage(), e);
            return false;
        }
    }

    @Override
    @Transactional
    public void updateCartStatus(String userId, String oldStatus, String newStatus) {
        logger.debug("Updating cart status for user: {} from {} to {}", userId, oldStatus, newStatus);

        try {
            validateUserId(userId);
            validateStatus(oldStatus);
            validateStatus(newStatus);

            List<CartEntity> carts = cartRepository.findByUserIdAndStatus(userId, oldStatus);

            if (carts.isEmpty()) {
                logger.info("No carts found with status {} for user {}", oldStatus, userId);
                return;
            }

            int updatedCount = 0;
            for (CartEntity cart : carts) {
                cart.setStatus(newStatus);
                cart.setLastUpdated(LocalDateTime.now());

                try {
                    cartRepository.save(cart);
                    updatedCount++;
                    logger.debug("Updated cart ID {} status to {}", cart.getId(), newStatus);
                } catch (Exception e) {
                    logger.error("Failed to update cart ID {} status: {}", cart.getId(), e.getMessage());
                }
            }

            logger.info("Successfully updated {} cart(s) status from {} to {} for user {}",
                    updatedCount, oldStatus, newStatus, userId);

        } catch (Exception e) {
            logger.error("Error updating cart status for user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Failed to update cart status", e);
        }
    }

    /**
     * Clean up expired carts for all users
     */
    @Transactional
    public int cleanupExpiredCarts() {
        logger.info("Starting cleanup of expired carts");

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(CART_EXPIRY_HOURS);

            // Find active carts that are older than the cutoff time
            List<CartEntity> expiredCarts = cartRepository.findActiveCartsOlderThan(cutoffTime);

            if (expiredCarts.isEmpty()) {
                logger.info("No expired carts found for cleanup");
                return 0;
            }

            logger.info("Found {} expired carts to clean up", expiredCarts.size());

            int cleanedCount = 0;
            for (CartEntity cart : expiredCarts) {
                try {
                    cart.setStatus(ABANDONED_STATUS);
                    cart.setLastUpdated(LocalDateTime.now());
                    cartRepository.save(cart);
                    cleanedCount++;

                    logger.debug("Abandoned expired cart ID {} for user {}", cart.getId(), cart.getUserId());
                } catch (Exception e) {
                    logger.error("Failed to abandon expired cart ID {}: {}", cart.getId(), e.getMessage());
                }
            }

            logger.info("Successfully cleaned up {} expired carts", cleanedCount);
            return cleanedCount;

        } catch (Exception e) {
            logger.error("Error during expired cart cleanup: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Get cart statistics for monitoring
     */
    @Transactional(readOnly = true)
    public CartStatistics getCartStatistics() {
        logger.debug("Retrieving cart statistics");

        try {
            long activeCount = cartRepository.countByStatus(ACTIVE_STATUS);
            long abandonedCount = cartRepository.countByStatus(ABANDONED_STATUS);
            long completedCount = cartRepository.countByStatus(COMPLETED_STATUS);

            LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(24);
            long recentActiveCount = cartRepository.countActiveCartsSince(oneDayAgo);

            CartStatistics stats = new CartStatistics(activeCount, abandonedCount, completedCount, recentActiveCount);
            logger.debug("Cart statistics: {}", stats);

            return stats;

        } catch (Exception e) {
            logger.error("Error retrieving cart statistics: {}", e.getMessage(), e);
            return new CartStatistics(0, 0, 0, 0);
        }
    }

    /**
     * Handle multiple carts scenario (should not happen but defensive programming)
     */
    private Optional<CartModel> handleMultipleCarts(List<CartEntity> cartEntities, String userId, String status) {
        // Sort by last updated, keep the most recent
        List<CartEntity> sortedCarts = cartEntities.stream()
                .sorted((c1, c2) -> c2.getLastUpdated().compareTo(c1.getLastUpdated()))
                .collect(Collectors.toList());

        CartEntity latestCart = sortedCarts.get(0);

        // Mark older carts as abandoned
        for (int i = 1; i < sortedCarts.size(); i++) {
            CartEntity oldCart = sortedCarts.get(i);
            try {
                oldCart.setStatus(ABANDONED_STATUS);
                oldCart.setLastUpdated(LocalDateTime.now());
                cartRepository.save(oldCart);
                logger.info("Abandoned duplicate cart ID {} for user {}", oldCart.getId(), userId);
            } catch (Exception e) {
                logger.error("Failed to abandon duplicate cart ID {}: {}", oldCart.getId(), e.getMessage());
            }
        }

        return Optional.of(cartEntityMapper.toModel(latestCart));
    }

    /**
     * Abandon an expired cart
     */
    private void abandonExpiredCart(CartEntity cartEntity) {
        try {
            cartEntity.setStatus(ABANDONED_STATUS);
            cartEntity.setLastUpdated(LocalDateTime.now());
            cartRepository.save(cartEntity);
            logger.info("Abandoned expired cart ID {} for user {}", cartEntity.getId(), cartEntity.getUserId());
        } catch (Exception e) {
            logger.error("Failed to abandon expired cart ID {}: {}", cartEntity.getId(), e.getMessage());
        }
    }

    /**
     * Validate cart before saving
     */
    private void validateCartForSave(CartModel cartModel) {
        if (cartModel == null) {
            throw new IllegalArgumentException("Cart model cannot be null");
        }

        if (!cartModel.isValid()) {
            throw new IllegalArgumentException("Cart model is not valid: " + cartModel.getSummary());
        }

        validateUserId(cartModel.getUserId());
        validateStatus(cartModel.getStatus());
    }

    /**
     * Validate user ID
     */
    private void validateUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
    }

    /**
     * Validate status
     */
    private void validateStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be null or empty");
        }

        if (!ACTIVE_STATUS.equals(status) && !ABANDONED_STATUS.equals(status) && !COMPLETED_STATUS.equals(status)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }

    /**
     * Cart statistics data class
     */
    public static class CartStatistics {
        private final long activeCount;
        private final long abandonedCount;
        private final long completedCount;
        private final long recentActiveCount;

        public CartStatistics(long activeCount, long abandonedCount, long completedCount, long recentActiveCount) {
            this.activeCount = activeCount;
            this.abandonedCount = abandonedCount;
            this.completedCount = completedCount;
            this.recentActiveCount = recentActiveCount;
        }

        public long getActiveCount() { return activeCount; }
        public long getAbandonedCount() { return abandonedCount; }
        public long getCompletedCount() { return completedCount; }
        public long getRecentActiveCount() { return recentActiveCount; }

        @Override
        public String toString() {
            return String.format("CartStatistics[active=%d, abandoned=%d, completed=%d, recentActive=%d]",
                    activeCount, abandonedCount, completedCount, recentActiveCount);
        }
    }
}
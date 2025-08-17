package com.rockburger.cartservice.adapters.driven.jpa.mysql.adapter;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartItemEntity;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper.ICartItemEntityMapper;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.repository.ICartItemRepository;
import com.rockburger.cartservice.domain.exception.CartItemNotFoundException;
import com.rockburger.cartservice.domain.model.CartItemModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.ConstraintViolationException;
import java.util.Optional;

@Service
public class CartItemAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CartItemAdapter.class);

    private final ICartItemRepository cartItemRepository;
    private final ICartItemEntityMapper cartItemEntityMapper;

    public CartItemAdapter(ICartItemRepository cartItemRepository,
                           ICartItemEntityMapper cartItemEntityMapper) {
        this.cartItemRepository = cartItemRepository;
        this.cartItemEntityMapper = cartItemEntityMapper;
    }

    /**
     * Saves a cart item. The entity will automatically set timestamps and calculate subtotal
     * via JPA lifecycle callbacks (@PrePersist/@PreUpdate).
     */
    @Transactional
    public CartItemModel save(CartItemModel itemModel) {
        try {
            logger.debug("Saving cart item for article: {} with quantity: {} and price: {}",
                    itemModel.getArticleId(), itemModel.getQuantity(), itemModel.getPrice());

            CartItemEntity itemEntity = cartItemEntityMapper.toEntity(itemModel);

            // Validate that the entity has all required fields
            validateCartItem(itemEntity);

            CartItemEntity savedEntity = cartItemRepository.save(itemEntity);

            logger.debug("Cart item saved with ID: {}, subtotal calculated as: {}",
                    savedEntity.getId(), savedEntity.getSubtotal());

            return cartItemEntityMapper.toModel(savedEntity);

        } catch (ConstraintViolationException e) {
            logger.error("Validation error saving cart item: {}", e.getMessage());
            throw new RuntimeException("Invalid cart item data: " + e.getMessage(), e);
        } catch (DataIntegrityViolationException e) {
            logger.error("Data integrity error saving cart item: {}", e.getMessage());
            throw new RuntimeException("Cart item data integrity violation", e);
        } catch (Exception e) {
            logger.error("Unexpected error saving cart item: ", e);
            throw new RuntimeException("Failed to save cart item", e);
        }
    }

    /**
     * Updates an existing cart item. The entity will automatically update timestamp
     * and recalculate subtotal via @PreUpdate callback.
     */
    @Transactional
    public CartItemModel update(CartItemModel itemModel) {
        try {
            logger.debug("Updating cart item ID: {} for article: {}",
                    itemModel.getId(), itemModel.getArticleId());

            if (itemModel.getId() == null) {
                throw new IllegalArgumentException("Cart item ID is required for update");
            }

            CartItemEntity existingEntity = cartItemRepository.findById(itemModel.getId())
                    .orElseThrow(() -> new CartItemNotFoundException("Cart item not found with ID: " + itemModel.getId()));

            // Update entity fields (mapper will ignore timestamps and version)
            cartItemEntityMapper.updateEntity(existingEntity, itemModel);

            CartItemEntity savedEntity = cartItemRepository.save(existingEntity);

            logger.debug("Cart item updated with new subtotal: {}", savedEntity.getSubtotal());

            return cartItemEntityMapper.toModel(savedEntity);

        } catch (CartItemNotFoundException e) {
            throw e; // Re-throw as-is
        } catch (Exception e) {
            logger.error("Error updating cart item: ", e);
            throw new RuntimeException("Failed to update cart item", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<CartItemModel> findByCartIdAndArticleId(Long cartId, Long articleId) {
        logger.debug("Finding cart item for cart: {} and article: {}", cartId, articleId);

        try {
            return cartItemRepository.findByCartIdAndArticleId(cartId, articleId)
                    .map(cartItemEntityMapper::toModel);
        } catch (Exception e) {
            logger.error("Error finding cart item: ", e);
            throw new RuntimeException("Failed to find cart item", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<CartItemModel> findById(Long id) {
        logger.debug("Finding cart item by ID: {}", id);

        try {
            return cartItemRepository.findById(id)
                    .map(cartItemEntityMapper::toModel);
        } catch (Exception e) {
            logger.error("Error finding cart item by ID: ", e);
            throw new RuntimeException("Failed to find cart item", e);
        }
    }

    @Transactional
    public void deleteByCartIdAndArticleId(Long cartId, Long articleId) {
        logger.debug("Deleting cart item for cart: {} and article: {}", cartId, articleId);

        try {
            if (!cartItemRepository.existsByCartIdAndArticleId(cartId, articleId)) {
                throw new CartItemNotFoundException(
                        String.format("Cart item not found for cart %d and article %d", cartId, articleId));
            }

            cartItemRepository.deleteByCartIdAndArticleId(cartId, articleId);

            logger.debug("Cart item deleted for cart: {} and article: {}", cartId, articleId);

        } catch (CartItemNotFoundException e) {
            throw e; // Re-throw as-is
        } catch (Exception e) {
            logger.error("Error deleting cart item: ", e);
            throw new RuntimeException("Failed to delete cart item", e);
        }
    }

    @Transactional
    public void deleteById(Long id) {
        logger.debug("Deleting cart item by ID: {}", id);

        try {
            if (!cartItemRepository.existsById(id)) {
                throw new CartItemNotFoundException("Cart item not found with ID: " + id);
            }

            cartItemRepository.deleteById(id);

            logger.debug("Cart item deleted with ID: {}", id);

        } catch (CartItemNotFoundException e) {
            throw e; // Re-throw as-is
        } catch (Exception e) {
            logger.error("Error deleting cart item by ID: ", e);
            throw new RuntimeException("Failed to delete cart item", e);
        }
    }

    /**
     * Validates that a cart item entity has all required fields before saving.
     */
    private void validateCartItem(CartItemEntity entity) {
        if (entity.getArticleId() == null) {
            throw new IllegalArgumentException("Article ID is required");
        }
        if (entity.getArticleName() == null || entity.getArticleName().trim().isEmpty()) {
            throw new IllegalArgumentException("Article name is required");
        }
        if (entity.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (entity.getPrice() < 0) {
            throw new IllegalArgumentException("Price must be greater than or equal to zero");
        }
        if (entity.getCart() == null) {
            throw new IllegalArgumentException("Cart reference is required");
        }
    }
}
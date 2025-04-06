package com.rockburger.cartservice.adapters.driven.jpa.mysql.adapter;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartItemEntity;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.mapper.ICartItemEntityMapper;
import com.rockburger.cartservice.adapters.driven.jpa.mysql.repository.ICartItemRepository;
import com.rockburger.cartservice.domain.exception.CartItemNotFoundException;
import com.rockburger.cartservice.domain.model.CartItemModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public CartItemModel save(CartItemModel itemModel) {
        try {
            logger.debug("Saving cart item for article: {}", itemModel.getArticleId());
            CartItemEntity itemEntity = cartItemEntityMapper.toEntity(itemModel);
            CartItemEntity savedEntity = cartItemRepository.save(itemEntity);
            return cartItemEntityMapper.toModel(savedEntity);
        } catch (Exception e) {
            logger.error("Error saving cart item: ", e);
            throw new RuntimeException("Failed to save cart item", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<CartItemModel> findByCartIdAndArticleId(Long cartId, Long articleId) {
        return cartItemRepository.findByCartIdAndArticleId(cartId, articleId)
                .map(cartItemEntityMapper::toModel);
    }

    @Transactional
    public void deleteByCartIdAndArticleId(Long cartId, Long articleId) {
        if (!cartItemRepository.existsByCartIdAndArticleId(cartId, articleId)) {
            throw new CartItemNotFoundException("Cart item not found");
        }
        cartItemRepository.deleteByCartIdAndArticleId(cartId, articleId);
    }
}
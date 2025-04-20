package com.rockburger.cartservice.adapters.driven.jpa.mysql.repository;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ICartItemRepository extends JpaRepository<CartItemEntity, Long> {
    Optional<CartItemEntity> findByCartIdAndArticleId(Long cartId, Long articleId);

    @Modifying
    @Query("DELETE FROM CartItemEntity ci WHERE ci.cart.id = ?1 AND ci.articleId = ?2")
    void deleteByCartIdAndArticleId(Long cartId, Long articleId);

    boolean existsByCartIdAndArticleId(Long cartId, Long articleId);
}
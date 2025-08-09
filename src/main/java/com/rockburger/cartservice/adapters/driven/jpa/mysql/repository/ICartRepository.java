package com.rockburger.cartservice.adapters.driven.jpa.mysql.repository;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ICartRepository extends JpaRepository<CartEntity, Long> {

    /**
     * Finds the most recent cart for a user with the specified status
     * Orders by last updated date descending to get the most recent cart
     */
    @Query("SELECT c FROM CartEntity c WHERE c.userId = :userId AND c.status = :status ORDER BY c.lastUpdated DESC")
    Optional<CartEntity> findByUserIdAndStatusOrderByLastUpdatedDesc(
            @Param("userId") String userId,
            @Param("status") String status);

    /**
     * Finds all carts for a user with the specified status
     */
    List<CartEntity> findByUserIdAndStatus(String userId, String status);

    /**
     * Finds all carts for a specific user
     */
    List<CartEntity> findByUserId(String userId);

    /**
     * Checks if a cart exists for a user with the specified status
     */
    boolean existsByUserIdAndStatus(String userId, String status);

    /**
     * Deletes all carts for a user with the specified status
     */
    void deleteByUserIdAndStatus(String userId, String status);
}
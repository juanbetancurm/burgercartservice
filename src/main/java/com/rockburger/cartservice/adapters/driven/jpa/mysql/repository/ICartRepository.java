package com.rockburger.cartservice.adapters.driven.jpa.mysql.repository;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ICartRepository extends JpaRepository<CartEntity, Long> {

    // Existing methods (keep these)
    List<CartEntity> findByUserIdAndStatus(String userId, String status);
    boolean existsByUserIdAndStatus(String userId, String status);

    // Add these missing methods:

    /**
     * Find all carts for a specific user
     */
    List<CartEntity> findByUserId(String userId);

    /**
     * Find active carts older than the specified cutoff time
     */
    @Query("SELECT c FROM CartEntity c WHERE c.status = 'ACTIVE' AND c.lastUpdated < :cutoffTime")
    List<CartEntity> findActiveCartsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Count carts by status
     */
    long countByStatus(String status);

    /**
     * Count active carts since a specific date
     */
    @Query("SELECT COUNT(c) FROM CartEntity c WHERE c.status = 'ACTIVE' AND c.lastUpdated >= :since")
    long countActiveCartsSince(@Param("since") LocalDateTime since);
}
package com.rockburger.cartservice.adapters.driven.jpa.mysql.repository;

import com.rockburger.cartservice.adapters.driven.jpa.mysql.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ICartRepository extends JpaRepository<CartEntity, Long> {
    Optional<CartEntity> findByUserIdAndStatus(String userId, String status);

    boolean existsByUserIdAndStatus(String userId, String status);

    @Modifying
    @Query("UPDATE CartEntity c SET c.status = :newStatus, c.lastUpdated = CURRENT_TIMESTAMP " +
            "WHERE c.userId = :userId AND c.status = :oldStatus")
    int updateCartStatus(@Param("userId") String userId,
                         @Param("oldStatus") String oldStatus,
                         @Param("newStatus") String newStatus);

    @Modifying
    @Query("DELETE FROM CartEntity c WHERE c.status = 'ACTIVE' AND c.lastUpdated < :expirationTime")
    void deleteExpiredCarts(@Param("expirationTime") LocalDateTime expirationTime);
}

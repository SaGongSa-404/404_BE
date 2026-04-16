package com.example._04_backend.domain.wish.repository;

import com.example._04_backend.domain.wish.entity.Wish;
import com.example._04_backend.domain.wish.enums.WishStatus;
import com.example._04_backend.global.common.enums.Category;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface WishRepository extends JpaRepository<Wish, UUID> {

    @Query("""
            SELECT w FROM Wish w
            WHERE w.user.id = :userId
              AND (:status IS NULL OR w.status = :status)
              AND w.decisionAt >= :from
              AND w.decisionAt < :to
            ORDER BY w.decisionAt DESC
            """)
    List<Wish> findByUserIdAndStatusAndDecisionAtBetween(
            @Param("userId") UUID userId,
            @Param("status") WishStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("""
            SELECT w FROM Wish w
            WHERE w.user.id = :userId
              AND w.status = :status
              AND w.decisionAt >= :from
              AND w.decisionAt < :to
            """)
    List<Wish> findAllByUserIdAndStatusAndDecisionAtBetween(
            @Param("userId") UUID userId,
            @Param("status") WishStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(SUM(w.price), 0) FROM Wish w
            WHERE w.user.id = :userId
              AND w.status = :status
              AND w.decisionAt >= :from
              AND w.decisionAt < :to
            """)
    Integer sumPriceByUserIdAndStatusAndDecisionAtBetween(
            @Param("userId") UUID userId,
            @Param("status") WishStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(w) FROM Wish w
            WHERE w.user.id = :userId
              AND w.status = :status
              AND w.decisionAt >= :from
              AND w.decisionAt < :to
            """)
    long countByUserIdAndStatusAndDecisionAtBetween(
            @Param("userId") UUID userId,
            @Param("status") WishStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT w.category, COALESCE(SUM(w.price), 0), COUNT(w)
            FROM Wish w
            WHERE w.user.id = :userId
              AND w.status = 'BOUGHT'
              AND w.decisionAt >= :from
              AND w.decisionAt < :to
            GROUP BY w.category
            """)
    List<Object[]> findCategoryStatsByUserIdAndDecisionAtBetween(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    void deleteByUserId(UUID userId);
}

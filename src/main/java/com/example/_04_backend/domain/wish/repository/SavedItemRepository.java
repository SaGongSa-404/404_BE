package com.example._04_backend.domain.wish.repository;

import com.example._04_backend.domain.wish.entity.SavedItem;
import com.example._04_backend.domain.wish.enums.ItemStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SavedItemRepository extends JpaRepository<SavedItem, UUID> {

    @Query("""
            SELECT s FROM SavedItem s
            WHERE s.user.id = :userId
              AND (:status IS NULL OR s.status = :status)
              AND s.updatedAt >= :from
              AND s.updatedAt < :to
            ORDER BY s.updatedAt DESC
            """)
    List<SavedItem> findByUserIdAndStatusAndDecidedAtBetween(
            @Param("userId") UUID userId,
            @Param("status") ItemStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(s.listedPrice), 0) FROM SavedItem s
            WHERE s.user.id = :userId
              AND s.status = :status
              AND s.updatedAt >= :from
              AND s.updatedAt < :to
            """)
    Integer sumPriceByUserIdAndStatusAndDecidedAtBetween(
            @Param("userId") UUID userId,
            @Param("status") ItemStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(s) FROM SavedItem s
            WHERE s.user.id = :userId
              AND s.status = :status
              AND s.updatedAt >= :from
              AND s.updatedAt < :to
            """)
    long countByUserIdAndStatusAndDecidedAtBetween(
            @Param("userId") UUID userId,
            @Param("status") ItemStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT DISTINCT YEAR(s.updatedAt), MONTH(s.updatedAt)
            FROM SavedItem s
            WHERE s.user.id = :userId
              AND s.status IN :statuses
            ORDER BY YEAR(s.updatedAt) DESC, MONTH(s.updatedAt) DESC
            """)
    List<Object[]> findDistinctDecidedYearMonthsByUserId(
            @Param("userId") UUID userId,
            @Param("statuses") List<ItemStatus> statuses);

    void deleteByUserId(UUID userId);
}

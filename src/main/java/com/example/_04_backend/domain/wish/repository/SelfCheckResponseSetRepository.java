package com.example._04_backend.domain.wish.repository;

import com.example._04_backend.domain.wish.entity.SelfCheckResponseSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface SelfCheckResponseSetRepository extends JpaRepository<SelfCheckResponseSet, UUID> {

    /**
     * 이번 달 비합리적 선택 수 (IRRATIONAL 판정 + GO 결정)
     */
    @Query("""
            SELECT COUNT(s) FROM SelfCheckResponseSet s
            WHERE s.item.user.id = :userId
              AND s.item.status = 'GO'
              AND s.rationalityResult = 'IRRATIONAL'
              AND s.item.updatedAt >= :from
              AND s.item.updatedAt < :to
            """)
    long countIrrationalByUserIdAndDecidedAtBetween(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    boolean existsByItemId(UUID itemId);
}

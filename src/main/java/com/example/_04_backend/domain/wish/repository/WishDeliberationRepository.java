package com.example._04_backend.domain.wish.repository;

import com.example._04_backend.domain.wish.entity.WishDeliberation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface WishDeliberationRepository extends JpaRepository<WishDeliberation, UUID> {

    /**
     * 이번 달 비합리적 선택 수:
     * 경고가 발동된 채로(warningTriggered=true) BOUGHT 결정한 위시의 수
     */
    @Query("""
            SELECT COUNT(wd) FROM WishDeliberation wd
            WHERE wd.wish.user.id = :userId
              AND wd.wish.status = 'BOUGHT'
              AND wd.warningTriggered = true
              AND wd.wish.decisionAt >= :from
              AND wd.wish.decisionAt < :to
            """)
    long countIrrationalByUserIdAndDecisionAtBetween(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    boolean existsByWishId(UUID wishId);
}

package com.example._04_backend.domain.wish.repository;

import com.example._04_backend.domain.wish.entity.BudgetCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetCycleRepository extends JpaRepository<BudgetCycle, UUID> {

    Optional<BudgetCycle> findByUserIdAndYearMonth(UUID userId, String yearMonth);

    @Query("SELECT b.yearMonth FROM BudgetCycle b WHERE b.user.id = :userId ORDER BY b.yearMonth DESC")
    List<String> findYearMonthsByUserId(@Param("userId") UUID userId);

    void deleteByUserId(UUID userId);
}

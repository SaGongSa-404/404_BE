package com.example._04_backend.domain.wish.repository;

import com.example._04_backend.domain.wish.entity.BudgetCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BudgetCycleRepository extends JpaRepository<BudgetCycle, UUID> {

    Optional<BudgetCycle> findByUserIdAndYearMonth(UUID userId, String yearMonth);

    void deleteByUserId(UUID userId);
}

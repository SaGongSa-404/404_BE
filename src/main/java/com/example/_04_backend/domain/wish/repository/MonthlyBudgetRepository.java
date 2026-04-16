package com.example._04_backend.domain.wish.repository;

import com.example._04_backend.domain.wish.entity.MonthlyBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, UUID> {

    Optional<MonthlyBudget> findByUserIdAndYearMonth(UUID userId, String yearMonth);

    void deleteByUserId(UUID userId);
}

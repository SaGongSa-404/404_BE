package com.sagongsa.backend.domain.budget;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetCycleRepository extends JpaRepository<BudgetCycle, UUID> {

	Optional<BudgetCycle> findByUserIdAndYearMonth(UUID userId, String yearMonth);

	@Query("SELECT b.yearMonth FROM BudgetCycle b WHERE b.user.id = :userId ORDER BY b.yearMonth DESC")
	List<String> findYearMonthsByUserId(@Param("userId") UUID userId);

	@Modifying
	@Query("DELETE FROM BudgetCycle b WHERE b.user.id = :userId")
	void deleteByUserId(@Param("userId") UUID userId);
}

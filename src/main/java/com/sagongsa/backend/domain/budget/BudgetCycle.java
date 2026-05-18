package com.sagongsa.backend.domain.budget;

import com.sagongsa.backend.domain.common.UserScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
	name = "budget_cycles",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_budget_cycles_user_year_month", columnNames = {"user_id", "year_month"})
	}
)
public class BudgetCycle extends UserScopedEntity {

	@Column(name = "year_month", nullable = false, columnDefinition = "char(7)")
	@JdbcTypeCode(SqlTypes.CHAR)
	private String yearMonth;

	@Column(name = "monthly_budget_amount", nullable = false)
	private int monthlyBudgetAmount;

	@Column(name = "spent_amount", nullable = false)
	private int spentAmount;

	@Column(name = "warning_threshold_rate", nullable = false, precision = 5, scale = 2)
	private BigDecimal warningThresholdRate;

	@Column(name = "budget_exhaustion_bubble_seen", nullable = false)
	private boolean budgetExhaustionBubbleSeen;

	protected BudgetCycle() {
	}
}

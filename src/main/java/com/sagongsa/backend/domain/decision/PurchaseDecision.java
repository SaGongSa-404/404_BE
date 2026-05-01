package com.sagongsa.backend.domain.decision;

import com.sagongsa.backend.domain.budget.BudgetCycle;
import com.sagongsa.backend.domain.common.UserScopedEntity;
import com.sagongsa.backend.domain.enums.PurchaseDecisionResult;
import com.sagongsa.backend.domain.enums.RationalityResult;
import com.sagongsa.backend.domain.item.SavedItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
	name = "purchase_decisions",
	indexes = {
		@Index(name = "idx_purchase_decisions_user_decided_at", columnList = "user_id,decided_at")
	},
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_purchase_decisions_item_id", columnNames = "item_id")
	}
)
public class PurchaseDecision extends UserScopedEntity {

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private SavedItem item;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "budget_cycle_id")
	private BudgetCycle budgetCycle;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PurchaseDecisionResult result;

	@Column(name = "final_price")
	private Integer finalPrice;

	@Column(name = "budget_after_amount")
	private Integer budgetAfterAmount;

	@Column(name = "similar_category_spend_amount")
	private Integer similarCategorySpendAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RationalityResult rationalityResult;

	@Column(nullable = false)
	private short selfCheckYesCount;

	@Column(columnDefinition = "text")
	private String rationaleText;

	@Column(nullable = false)
	private boolean isChanged;

	@Column(nullable = false)
	private int changeCount;

	@Column
	private Instant changedAt;

	@Column(nullable = false)
	private Instant decidedAt;

	protected PurchaseDecision() {
	}
}

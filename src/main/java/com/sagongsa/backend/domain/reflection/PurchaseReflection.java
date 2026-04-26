package com.sagongsa.backend.domain.reflection;

import com.sagongsa.backend.domain.common.UserScopedEntity;
import com.sagongsa.backend.domain.decision.PurchaseDecision;
import com.sagongsa.backend.domain.enums.ReflectionRegretLevel;
import com.sagongsa.backend.domain.item.SavedItem;
import com.sagongsa.backend.domain.notification.ReminderSchedule;
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
	name = "purchase_reflections",
	indexes = {
		@Index(name = "idx_purchase_reflections_user_reflected_at", columnList = "user_id,reflected_at")
	},
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_purchase_reflections_decision_id", columnNames = "decision_id")
	}
)
public class PurchaseReflection extends UserScopedEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "item_id", nullable = false)
	private SavedItem item;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "decision_id", nullable = false)
	private PurchaseDecision decision;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reminder_id")
	private ReminderSchedule reminder;

	@Column
	private Short satisfactionScore;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReflectionRegretLevel regretLevel;

	@Column
	private Boolean stillUsing;

	@Column(columnDefinition = "text")
	private String reflectionNote;

	@Column(nullable = false)
	private Instant reflectedAt;

	protected PurchaseReflection() {
	}
}

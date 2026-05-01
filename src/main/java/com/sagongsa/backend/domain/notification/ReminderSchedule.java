package com.sagongsa.backend.domain.notification;

import com.sagongsa.backend.domain.common.UserScopedEntity;
import com.sagongsa.backend.domain.decision.PurchaseDecision;
import com.sagongsa.backend.domain.enums.ReminderStatus;
import com.sagongsa.backend.domain.enums.ReminderType;
import com.sagongsa.backend.domain.item.SavedItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
	name = "reminder_schedules",
	indexes = {
		@Index(name = "idx_reminder_schedules_user_scheduled_for", columnList = "user_id,scheduled_for"),
		@Index(name = "idx_reminder_schedules_status_scheduled_for", columnList = "status,scheduled_for")
	},
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_reminder_schedules_decision_type", columnNames = {"decision_id", "reminder_type"})
	}
)
public class ReminderSchedule extends UserScopedEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "item_id")
	private SavedItem item;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decision_id")
	private PurchaseDecision decision;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private ReminderType reminderType;

	@Column(nullable = false)
	private Instant scheduledFor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReminderStatus status = ReminderStatus.SCHEDULED;

	@Column
	private Instant sentAt;

	@Column
	private Instant canceledAt;

	@Column(length = 80)
	private String cancelReason;

	protected ReminderSchedule() {
	}
}

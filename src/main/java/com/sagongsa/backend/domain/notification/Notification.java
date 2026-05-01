package com.sagongsa.backend.domain.notification;

import com.sagongsa.backend.domain.common.UserScopedEntity;
import com.sagongsa.backend.domain.decision.PurchaseDecision;
import com.sagongsa.backend.domain.enums.NotificationType;
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
import java.time.Instant;

@Entity
@Table(
	name = "notifications",
	indexes = {
		@Index(name = "idx_notifications_user_read_created", columnList = "user_id,is_read,created_at"),
		@Index(name = "idx_notifications_user_created", columnList = "user_id,created_at")
	}
)
public class Notification extends UserScopedEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private NotificationType notificationType;

	@Column(nullable = false, length = 140)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String body;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "item_id")
	private SavedItem item;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decision_id")
	private PurchaseDecision decision;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reminder_id")
	private ReminderSchedule reminder;

	@Column(columnDefinition = "text")
	private String targetPath;

	@Column(nullable = false)
	private boolean isRead;

	@Column
	private Instant readAt;

	protected Notification() {
	}
}

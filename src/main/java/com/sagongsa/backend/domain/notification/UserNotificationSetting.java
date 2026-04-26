package com.sagongsa.backend.domain.notification;

import com.sagongsa.backend.domain.auth.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_notification_settings")
public class UserNotificationSetting {

	@Id
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(nullable = false)
	private boolean pushEnabled = true;

	@Column(nullable = false)
	private boolean regretReminderEnabled = true;

	@Column(nullable = false)
	private boolean wishlistReminderEnabled;

	@Column(nullable = false)
	private boolean budgetWarningEnabled;

	@Column(nullable = false)
	private boolean socialVoteEnabled;

	@Column(nullable = false)
	private Instant updatedAt;

	protected UserNotificationSetting() {
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = Instant.now();
	}
}

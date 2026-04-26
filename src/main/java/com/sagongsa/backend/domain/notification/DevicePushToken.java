package com.sagongsa.backend.domain.notification;

import com.sagongsa.backend.domain.common.UserScopedEntity;
import com.sagongsa.backend.domain.enums.PushPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
	name = "device_push_tokens",
	indexes = {
		@Index(name = "idx_device_push_tokens_user_active", columnList = "user_id,is_active")
	},
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_device_push_tokens_push_token", columnNames = "push_token")
	}
)
public class DevicePushToken extends UserScopedEntity {

	@Column(length = 120)
	private String deviceId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PushPlatform platform;

	@Column(nullable = false, length = 512)
	private String pushToken;

	@Column(nullable = false)
	private boolean isActive = true;

	@Column
	private Instant disabledAt;

	protected DevicePushToken() {
	}
}

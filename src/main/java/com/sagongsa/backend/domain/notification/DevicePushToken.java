package com.sagongsa.backend.domain.notification;

import com.sagongsa.backend.domain.common.UserScopedEntity;
import com.sagongsa.backend.domain.auth.UserAccount;
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

	private DevicePushToken(UserAccount user, String deviceId, PushPlatform platform, String pushToken) {
		super(user);
		this.deviceId = normalizeDeviceId(deviceId);
		this.platform = platform;
		this.pushToken = pushToken;
		this.isActive = true;
	}

	public static DevicePushToken create(UserAccount user, String deviceId, PushPlatform platform, String pushToken) {
		return new DevicePushToken(user, deviceId, platform, pushToken);
	}

	public void activateFor(UserAccount user, String deviceId, PushPlatform platform) {
		assignUser(user);
		this.deviceId = normalizeDeviceId(deviceId);
		this.platform = platform;
		this.isActive = true;
		this.disabledAt = null;
	}

	public void deactivate() {
		if (!isActive) {
			return;
		}
		this.isActive = false;
		this.disabledAt = Instant.now();
	}

	public String getDeviceId() {
		return deviceId;
	}

	public PushPlatform getPlatform() {
		return platform;
	}

	public String getPushToken() {
		return pushToken;
	}

	public boolean isActive() {
		return isActive;
	}

	public Instant getDisabledAt() {
		return disabledAt;
	}

	private static String normalizeDeviceId(String deviceId) {
		if (deviceId == null || deviceId.isBlank()) {
			return null;
		}
		return deviceId;
	}
}

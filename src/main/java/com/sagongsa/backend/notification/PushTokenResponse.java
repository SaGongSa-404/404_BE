package com.sagongsa.backend.notification;

import com.sagongsa.backend.domain.enums.PushPlatform;
import com.sagongsa.backend.domain.notification.DevicePushToken;
import java.util.UUID;

public record PushTokenResponse(
	UUID id,
	PushPlatform platform,
	String deviceId,
	boolean active
) {

	static PushTokenResponse of(DevicePushToken token) {
		return new PushTokenResponse(
			token.getId(),
			token.getPlatform(),
			token.getDeviceId(),
			token.isActive()
		);
	}
}

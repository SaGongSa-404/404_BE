package com.sagongsa.backend.mypage;

import jakarta.validation.constraints.NotNull;

record NotificationSettingsRequest(
	@NotNull(message = "알림 수신 여부는 필수입니다.")
	Boolean notificationEnabled
) {}

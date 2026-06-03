package com.sagongsa.backend.notification;

import com.sagongsa.backend.domain.enums.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PushTokenRegisterRequest(
	@NotBlank
	@Size(max = 512)
	String token,

	@NotNull
	PushPlatform platform,

	@Size(max = 120)
	String deviceId
) {
}

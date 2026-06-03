package com.sagongsa.backend.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushTokenDeleteRequest(
	@NotBlank
	@Size(max = 512)
	String token
) {
}

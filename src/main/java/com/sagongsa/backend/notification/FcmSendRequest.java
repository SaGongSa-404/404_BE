package com.sagongsa.backend.notification;

import java.util.Map;

public record FcmSendRequest(
	String token,
	String title,
	String body,
	Map<String, String> data
) {
}

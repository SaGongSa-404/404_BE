package com.sagongsa.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.push.fcm")
public record FirebasePushProperties(
	boolean enabled,
	String credentialsLocation
) {
}

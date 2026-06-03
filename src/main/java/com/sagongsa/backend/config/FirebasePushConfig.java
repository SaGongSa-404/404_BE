package com.sagongsa.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.sagongsa.backend.notification.FcmMessageSender;
import com.sagongsa.backend.notification.FirebaseFcmMessageSender;
import com.sagongsa.backend.notification.NoopFcmMessageSender;
import java.io.IOException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Configuration
@EnableConfigurationProperties(FirebasePushProperties.class)
public class FirebasePushConfig {

	private static final String FIREBASE_APP_NAME = "sagongsa-fcm";

	@Bean
	FcmMessageSender fcmMessageSender(FirebasePushProperties properties, ResourceLoader resourceLoader) throws IOException {
		if (!properties.enabled()) {
			return new NoopFcmMessageSender();
		}
		if (properties.credentialsLocation() == null || properties.credentialsLocation().isBlank()) {
			throw new IllegalStateException("app.push.fcm.credentials-location must be configured when FCM is enabled.");
		}

		FirebaseApp app = FirebaseApp.getApps().stream()
			.filter(existing -> FIREBASE_APP_NAME.equals(existing.getName()))
			.findFirst()
			.orElseGet(() -> initializeFirebaseApp(properties, resourceLoader));
		return new FirebaseFcmMessageSender(FirebaseMessaging.getInstance(app));
	}

	private FirebaseApp initializeFirebaseApp(FirebasePushProperties properties, ResourceLoader resourceLoader) {
		Resource resource = resourceLoader.getResource(properties.credentialsLocation());
		try (var inputStream = resource.getInputStream()) {
			FirebaseOptions options = FirebaseOptions.builder()
				.setCredentials(GoogleCredentials.fromStream(inputStream))
				.build();
			return FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to load Firebase credentials.", exception);
		}
	}
}

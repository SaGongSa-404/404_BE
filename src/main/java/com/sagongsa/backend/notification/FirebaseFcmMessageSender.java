package com.sagongsa.backend.notification;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FirebaseFcmMessageSender implements FcmMessageSender {

	private static final Logger log = LoggerFactory.getLogger(FirebaseFcmMessageSender.class);

	private final FirebaseMessaging firebaseMessaging;

	public FirebaseFcmMessageSender(FirebaseMessaging firebaseMessaging) {
		this.firebaseMessaging = firebaseMessaging;
	}

	@Override
	public FcmSendResult send(FcmSendRequest request) {
		AndroidConfig.Builder androidConfigBuilder = AndroidConfig.builder()
			.setPriority(AndroidConfig.Priority.HIGH);
		if (request.channelId() != null && !request.channelId().isBlank()) {
			androidConfigBuilder.setNotification(AndroidNotification.builder()
				.setChannelId(request.channelId())
				.build());
		}
		Message message = Message.builder()
			.setToken(request.token())
			.setNotification(Notification.builder()
				.setTitle(request.title())
				.setBody(request.body())
				.build())
			.putAllData(request.data())
			.setAndroidConfig(androidConfigBuilder.build())
			.build();
		try {
			firebaseMessaging.send(message);
			return FcmSendResult.success();
		}
		catch (FirebaseMessagingException exception) {
			if (isInvalidToken(exception)) {
				return FcmSendResult.invalid();
			}
			log.warn("Failed to send FCM message.", exception);
			return FcmSendResult.failed();
		}
	}

	private boolean isInvalidToken(FirebaseMessagingException exception) {
		return exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
			|| exception.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT
			|| exception.getErrorCode() == ErrorCode.INVALID_ARGUMENT;
	}
}

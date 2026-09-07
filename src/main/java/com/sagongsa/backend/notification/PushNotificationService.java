package com.sagongsa.backend.notification;

import com.sagongsa.backend.notification.PushDeliveryRepository.PushTokenTarget;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushNotificationService {

	private final PushDeliveryRepository repository;
	private final FcmMessageSender fcmMessageSender;

	public PushNotificationService(PushDeliveryRepository repository, FcmMessageSender fcmMessageSender) {
		this.repository = repository;
		this.fcmMessageSender = fcmMessageSender;
	}

	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
	public void send(NotificationPushMessage message) {
		if (!repository.pushEnabled(message.userId())) {
			return;
		}

		List<PushTokenTarget> tokens = repository.activeTokens(message.userId());
		for (PushTokenTarget token : tokens) {
			FcmSendResult result = fcmMessageSender.send(new FcmSendRequest(
				token.pushToken(),
				message.title(),
				message.body(),
				payload(message),
				message.channelId()
			));
			if (result.invalidToken()) {
				repository.deactivateToken(token.pushToken());
			}
		}
	}

	private Map<String, String> payload(NotificationPushMessage message) {
		Map<String, String> data = new LinkedHashMap<>();
		data.put("notificationId", message.notificationId().toString());
		data.put("notificationType", message.notificationType());
		if (message.targetPath() != null && !message.targetPath().isBlank()) {
			data.put("targetPath", message.targetPath());
		}
		if (message.channelId() != null && !message.channelId().isBlank()) {
			data.put("channelId", message.channelId());
		}
		return data;
	}

}

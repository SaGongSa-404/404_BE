package com.sagongsa.backend.notification;

import com.sagongsa.backend.auth.CurrentUserId;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	public List<NotificationResponse> list(
		@CurrentUserId UUID userId,
		@RequestParam(defaultValue = "false") boolean unreadOnly
	) {
		return notificationService.list(userId, unreadOnly);
	}

	@PatchMapping("/{notificationId}/read")
	public NotificationResponse markAsRead(
		@CurrentUserId UUID userId,
		@PathVariable UUID notificationId
	) {
		return notificationService.markAsRead(userId, notificationId);
	}
}

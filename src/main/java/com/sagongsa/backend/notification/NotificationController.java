package com.sagongsa.backend.notification;

import com.sagongsa.backend.auth.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Notification", description = "Notification list and read-state APIs")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	@Operation(
		summary = "List notifications",
		description = "Returns notifications for the authenticated user. Use unreadOnly=true to filter unread notifications.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Notifications returned"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
		}
	)
	public List<NotificationResponse> list(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@RequestParam(defaultValue = "false") boolean unreadOnly
	) {
		return notificationService.list(userId, unreadOnly);
	}

	@PatchMapping("/{notificationId}/read")
	@Operation(
		summary = "Mark notification as read",
		description = "Marks one notification as read and returns the updated notification payload.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Notification marked as read"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "403", description = "Notification belongs to another user"),
			@ApiResponse(responseCode = "404", description = "Notification does not exist")
		}
	)
	public NotificationResponse markAsRead(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@PathVariable UUID notificationId
	) {
		return notificationService.markAsRead(userId, notificationId);
	}
}

package com.sagongsa.backend.notification;

import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {

	private final NotificationBroadcastService notificationBroadcastService;

	public AdminNotificationController(NotificationBroadcastService notificationBroadcastService) {
		this.notificationBroadcastService = notificationBroadcastService;
	}

	@PostMapping("/app-update")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public NotificationBroadcastResponse publishAppUpdate(
		Authentication authentication,
		@RequestBody AppUpdateNotificationRequest request
	) {
		requireAdmin(authentication);
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required.");
		}
		if (request.version() == null || request.version().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "version is required.");
		}
		if (request.playStoreUrl() == null || request.playStoreUrl().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "playStoreUrl is required.");
		}
		int createdCount = notificationBroadcastService.publishAppUpdate(
			request.version().trim(),
			request.playStoreUrl().trim()
		);
		return new NotificationBroadcastResponse(createdCount);
	}

	@PostMapping("/maintenance")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public NotificationBroadcastResponse publishMaintenance(
		Authentication authentication,
		@RequestBody MaintenanceNotificationRequest request
	) {
		requireAdmin(authentication);
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required.");
		}
		if (request.startsAt() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startsAt is required.");
		}
		if (request.durationHours() <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationHours must be positive.");
		}
		int createdCount = notificationBroadcastService.publishMaintenanceNotice(
			request.startsAt(),
			request.durationHours()
		);
		return new NotificationBroadcastResponse(createdCount);
	}

	private void requireAdmin(Authentication authentication) {
		if (authentication == null || authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.noneMatch("ROLE_ADMIN"::equals)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin authority is required.");
		}
	}

	public record AppUpdateNotificationRequest(
		String version,
		String playStoreUrl
	) {
	}

	public record MaintenanceNotificationRequest(
		OffsetDateTime startsAt,
		int durationHours
	) {
	}

	public record NotificationBroadcastResponse(int createdCount) {
	}
}

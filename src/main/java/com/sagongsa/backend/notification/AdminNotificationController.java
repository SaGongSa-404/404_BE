package com.sagongsa.backend.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {

	private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

	private final NotificationBroadcastService notificationBroadcastService;
	private final String adminNotificationToken;

	public AdminNotificationController(
		NotificationBroadcastService notificationBroadcastService,
		@Value("${app.admin.notification-token:}") String adminNotificationToken
	) {
		this.notificationBroadcastService = notificationBroadcastService;
		this.adminNotificationToken = adminNotificationToken;
	}

	@PostMapping("/app-update")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public NotificationBroadcastResponse publishAppUpdate(
		Authentication authentication,
		@RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String adminToken,
		@RequestBody AppUpdateNotificationRequest request
	) {
		requireAdmin(authentication, adminToken);
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
		@RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) String adminToken,
		@RequestBody MaintenanceNotificationRequest request
	) {
		requireAdmin(authentication, adminToken);
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

	private void requireAdmin(Authentication authentication, String adminToken) {
		if (hasAdminRole(authentication) || hasAdminToken(adminToken)) {
			return;
		}
		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin authority is required.");
	}

	private boolean hasAdminRole(Authentication authentication) {
		return authentication != null && authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch("ROLE_ADMIN"::equals);
	}

	private boolean hasAdminToken(String adminToken) {
		if (!StringUtils.hasText(adminNotificationToken) || !StringUtils.hasText(adminToken)) {
			return false;
		}
		return MessageDigest.isEqual(
			adminNotificationToken.getBytes(StandardCharsets.UTF_8),
			adminToken.getBytes(StandardCharsets.UTF_8)
		);
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

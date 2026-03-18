package com.fourohfour.backend.modules.notification.api.http;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import com.fourohfour.backend.modules.notification.application.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/houses/{houseId}/notifications")
    public NotificationFeedResponse listHouseNotifications(
            Authentication authentication,
            @PathVariable UUID houseId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        Instant cursorInstant = cursor == null || cursor.isBlank() ? null : Instant.parse(cursor);
        NotificationService.NotificationFeedView view = notificationService.listHouseNotifications(
                CurrentAuthenticatedUser.userId(authentication),
                houseId,
                cursorInstant,
                limit
        );
        return toResponse(view);
    }

    @PostMapping("/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(Authentication authentication, @PathVariable UUID notificationId) {
        notificationService.markAsRead(CurrentAuthenticatedUser.userId(authentication), notificationId);
    }

    @PostMapping("/notifications/read-batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsReadBatch(Authentication authentication, @Valid @RequestBody BatchReadRequest request) {
        notificationService.markAsReadBatch(CurrentAuthenticatedUser.userId(authentication), request.notificationIds());
    }

    @PostMapping("/push-devices")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerPushDevice(Authentication authentication, @Valid @RequestBody RegisterPushDeviceRequest request) {
        notificationService.registerPushDevice(
                CurrentAuthenticatedUser.userId(authentication),
                new NotificationService.RegisterPushDeviceCommand(request.platform(), request.pushToken(), request.deviceId())
        );
    }

    @DeleteMapping("/push-devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivatePushDevice(Authentication authentication, @PathVariable UUID deviceId) {
        notificationService.deactivatePushDevice(CurrentAuthenticatedUser.userId(authentication), deviceId);
    }

    private NotificationFeedResponse toResponse(NotificationService.NotificationFeedView view) {
        return new NotificationFeedResponse(
                view.items().stream()
                        .map(item -> new NotificationItemResponse(
                                item.notificationId(),
                                item.houseId(),
                                item.type(),
                                item.title(),
                                item.body(),
                                item.payload(),
                                item.occurredAt(),
                                item.isRead()
                        ))
                        .toList(),
                view.unreadCount(),
                view.nextCursor()
        );
    }

    public record BatchReadRequest(@NotEmpty List<UUID> notificationIds) {
    }

    public record RegisterPushDeviceRequest(
            @NotBlank String platform,
            @NotBlank String pushToken,
            String deviceId
    ) {
    }

    public record NotificationFeedResponse(
            List<NotificationItemResponse> items,
            long unreadCount,
            String nextCursor
    ) {
    }

    public record NotificationItemResponse(
            UUID notificationId,
            UUID houseId,
            String type,
            String title,
            String body,
            Map<String, Object> payload,
            Instant occurredAt,
            boolean isRead
    ) {
    }
}


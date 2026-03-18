package com.fourohfour.backend.api.facades.notification;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import com.fourohfour.backend.modules.house.application.HouseService;
import com.fourohfour.backend.modules.notification.application.NotificationService;
import com.fourohfour.backend.modules.shared.api.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/facades/notifications")
public class NotificationFacadeController {

    private final NotificationService notificationService;
    private final HouseService houseService;

    public NotificationFacadeController(NotificationService notificationService, HouseService houseService) {
        this.notificationService = notificationService;
        this.houseService = houseService;
    }

    @GetMapping
    public NotificationService.NotificationFeedView getNotifications(
            Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        UUID userId = CurrentAuthenticatedUser.userId(authentication);
        UUID houseId = houseService.getActiveHouse(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "HOUSE_NOT_FOUND", "현재 참여 중인 집이 없습니다."))
                .houseId();
        Instant cursorInstant = cursor == null || cursor.isBlank() ? null : Instant.parse(cursor);
        return notificationService.listHouseNotifications(userId, houseId, cursorInstant, limit);
    }
}

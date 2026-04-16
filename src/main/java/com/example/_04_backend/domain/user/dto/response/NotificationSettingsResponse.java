package com.example._04_backend.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationSettingsResponse {
    private boolean notificationEnabled;
}

package com.example._04_backend.domain.user.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class NotificationSettingsRequest {

    @NotNull(message = "알림 수신 여부는 필수입니다.")
    private Boolean notificationEnabled;
}

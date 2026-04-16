package com.example._04_backend.domain.user.dto.response;

import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.domain.user.enums.ImpulseFrequency;
import com.example._04_backend.domain.user.enums.RaccoonStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class MyProfileResponse {

    private UUID id;
    private String nickname;
    private String raccoonName;
    private String email;
    private String profileImageUrl;
    private String provider;
    private RaccoonStatus raccoonStatus;
    private Integer monthlyBudget;
    private ImpulseFrequency impulseFrequency;
    private boolean notificationEnabled;
    private long postCount;
    private LocalDateTime createdAt;

    public static MyProfileResponse of(User user, long postCount) {
        return MyProfileResponse.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .raccoonName(user.getRaccoonName())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .provider(user.getProvider())
                .raccoonStatus(user.getRaccoonStatus())
                .monthlyBudget(user.getMonthlyBudget())
                .impulseFrequency(user.getImpulseFrequency())
                .notificationEnabled(user.isNotificationEnabled())
                .postCount(postCount)
                .createdAt(user.getCreatedAt())
                .build();
    }
}

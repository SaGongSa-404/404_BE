package com.example._04_backend.domain.user.dto.response;

import com.example._04_backend.domain.user.entity.User;
import com.example._04_backend.domain.user.entity.UserProfile;
import com.example._04_backend.domain.user.enums.OnboardingStatus;
import com.example._04_backend.domain.user.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class MyProfileResponse {

    private UUID id;
    private String nickname;
    private String mascotName;
    private String profileImageUrl;
    private String provider;
    private UserStatus status;
    private OnboardingStatus onboardingStatus;
    private long postCount;
    private LocalDateTime createdAt;

    public static MyProfileResponse of(User user, UserProfile profile, long postCount) {
        return MyProfileResponse.builder()
                .id(user.getId())
                .nickname(profile != null ? profile.getNickname() : null)
                .mascotName(profile != null ? profile.getMascotName() : null)
                .profileImageUrl(profile != null ? profile.getProfileImageUrl() : null)
                .provider(user.getProvider())
                .status(user.getStatus())
                .onboardingStatus(user.getOnboardingStatus())
                .postCount(postCount)
                .createdAt(user.getCreatedAt())
                .build();
    }
}

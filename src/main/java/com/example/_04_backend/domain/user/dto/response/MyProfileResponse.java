package com.example._04_backend.domain.user.dto.response;

import com.example._04_backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class MyProfileResponse {

    private UUID id;
    private String nickname;
    private String email;
    private String profileImageUrl;
    private String provider;
    private long postCount;

    public static MyProfileResponse of(User user, long postCount) {
        return MyProfileResponse.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .provider(user.getProvider())
                .postCount(postCount)
                .build();
    }
}

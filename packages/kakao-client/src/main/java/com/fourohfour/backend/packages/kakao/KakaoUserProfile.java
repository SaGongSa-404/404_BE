package com.fourohfour.backend.packages.kakao;

public record KakaoUserProfile(
        String providerUserId,
        String email,
        String nickname,
        String profileImageUrl
) {
}


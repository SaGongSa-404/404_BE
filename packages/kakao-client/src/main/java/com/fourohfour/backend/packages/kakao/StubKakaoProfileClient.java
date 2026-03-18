package com.fourohfour.backend.packages.kakao;

import org.springframework.stereotype.Component;

@Component
public class StubKakaoProfileClient implements KakaoProfileClient {

    @Override
    public KakaoUserProfile fetchProfile(String authorizationCode) {
        String normalized = authorizationCode == null ? "" : authorizationCode.trim();
        if (normalized.isEmpty()) {
            normalized = "anonymous";
        }

        String sanitized = normalized.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (sanitized.isEmpty()) {
            sanitized = "kakao";
        }
        String suffix = sanitized.length() > 8 ? sanitized.substring(0, 8) : sanitized;

        return new KakaoUserProfile(
                "kakao_" + sanitized,
                sanitized + "@kakao.local",
                "user-" + suffix,
                null
        );
    }
}


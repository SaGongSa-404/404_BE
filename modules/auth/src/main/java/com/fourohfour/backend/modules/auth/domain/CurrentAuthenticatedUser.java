package com.fourohfour.backend.modules.auth.domain;

import com.fourohfour.backend.modules.shared.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

public final class CurrentAuthenticatedUser {

    private CurrentAuthenticatedUser() {
    }

    public static UUID userId(Authentication authentication) {
        return principal(authentication).userId();
    }

    public static UUID sessionId(Authentication authentication) {
        return principal(authentication).sessionId();
    }

    private static SessionUserPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionUserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "인증이 필요합니다.");
        }
        return principal;
    }
}


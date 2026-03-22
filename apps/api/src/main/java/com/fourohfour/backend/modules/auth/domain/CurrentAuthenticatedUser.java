package com.fourohfour.backend.modules.auth.domain;

import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

public final class CurrentAuthenticatedUser {

    public static final UUID DEMO_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private CurrentAuthenticatedUser() {
    }

    public static UUID userId(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return DEMO_USER_ID;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SessionUserPrincipal sessionUserPrincipal) {
            return sessionUserPrincipal.userId();
        }
        if (principal instanceof String value && !value.isBlank()) {
            return UUID.fromString(value);
        }
        return DEMO_USER_ID;
    }
}


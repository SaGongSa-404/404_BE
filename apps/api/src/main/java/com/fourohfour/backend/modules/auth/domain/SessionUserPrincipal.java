package com.fourohfour.backend.modules.auth.domain;

import java.security.Principal;
import java.util.UUID;

public record SessionUserPrincipal(UUID userId) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}


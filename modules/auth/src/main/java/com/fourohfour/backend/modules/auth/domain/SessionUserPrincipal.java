package com.fourohfour.backend.modules.auth.domain;

import java.util.UUID;

public record SessionUserPrincipal(
        UUID sessionId,
        UUID userId
) {
}


package com.fourohfour.backend.modules.shared.api;

public record ApiErrorResponse(
        String code,
        String message
) {
}


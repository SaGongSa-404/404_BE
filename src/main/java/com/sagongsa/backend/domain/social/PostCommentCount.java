package com.sagongsa.backend.domain.social;

import java.util.UUID;

public record PostCommentCount(UUID postId, Long commentCount) {
}

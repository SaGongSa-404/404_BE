package com.example._04_backend.domain.social.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PostListResponse {

    private List<PostResponse> posts;
    private UUID nextCursor;
    private boolean hasMore;
}

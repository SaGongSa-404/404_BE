package com.example._04_backend.domain.social.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CommentListResponse {

    private List<CommentResponse> comments;
    private long total;
}

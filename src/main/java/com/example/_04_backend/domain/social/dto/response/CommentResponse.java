package com.example._04_backend.domain.social.dto.response;

import com.example._04_backend.domain.social.entity.Comment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class CommentResponse {

    private UUID id;
    private String body;
    private boolean isMine;
    private LocalDateTime createdAt;

    public static CommentResponse of(Comment comment, UUID currentUserId) {
        return CommentResponse.builder()
                .id(comment.getId())
                .body(comment.getBody())
                .isMine(comment.getUserId().equals(currentUserId))
                .createdAt(comment.getCreatedAt())
                .build();
    }
}

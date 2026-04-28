package com.example._04_backend.domain.social.dto.response;

import com.example._04_backend.domain.social.entity.FeedPost;
import com.example._04_backend.domain.social.enums.PostVoteType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class PostResponse {

    private UUID id;
    private UUID userId;
    private String title;
    private String body;
    private String imageUrl;
    private Integer price;
    private int goCount;
    private int stopCount;
    private long commentCount;
    private PostVoteType myVote;
    private LocalDateTime createdAt;

    public static PostResponse of(FeedPost post, long commentCount, PostVoteType myVote) {
        return PostResponse.builder()
                .id(post.getId())
                .userId(post.getUser().getId())
                .title(post.getTitle())
                .body(post.getBody())
                .imageUrl(post.getImageUrl())
                .price(post.getPrice())
                .goCount(post.getGoCount())
                .stopCount(post.getStopCount())
                .commentCount(commentCount)
                .myVote(myVote)
                .createdAt(post.getCreatedAt())
                .build();
    }
}

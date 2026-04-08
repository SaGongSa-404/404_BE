package com.example._04_backend.domain.social.dto.response;

import com.example._04_backend.domain.social.entity.SocialPost;
import com.example._04_backend.domain.social.enums.VoteType;
import com.example._04_backend.global.common.enums.Category;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class PostResponse {

    private UUID id;
    private String title;
    private String body;
    private String imageUrl;
    private String productUrl;
    private Integer price;
    private Category category;
    private int goCount;
    private int stopCount;
    private long commentCount;
    private VoteType myVote;
    private LocalDateTime createdAt;

    public static PostResponse of(SocialPost post, long commentCount, VoteType myVote) {
        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .body(post.getBody())
                .imageUrl(post.getImageUrl())
                .productUrl(post.getProductUrl())
                .price(post.getPrice())
                .category(post.getCategory())
                .goCount(post.getGoCount())
                .stopCount(post.getStopCount())
                .commentCount(commentCount)
                .myVote(myVote)
                .createdAt(post.getCreatedAt())
                .build();
    }
}

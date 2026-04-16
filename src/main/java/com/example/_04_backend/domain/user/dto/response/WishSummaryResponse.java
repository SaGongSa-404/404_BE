package com.example._04_backend.domain.user.dto.response;

import com.example._04_backend.domain.wish.entity.Wish;
import com.example._04_backend.domain.wish.enums.WishStatus;
import com.example._04_backend.global.common.enums.Category;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class WishSummaryResponse {
    private UUID id;
    private String title;
    private Integer price;
    private String imageUrl;
    private Category category;
    private WishStatus status;
    private LocalDateTime decisionAt;

    public static WishSummaryResponse of(Wish wish) {
        return WishSummaryResponse.builder()
                .id(wish.getId())
                .title(wish.getTitle())
                .price(wish.getPrice())
                .imageUrl(wish.getImageUrl())
                .category(wish.getCategory())
                .status(wish.getStatus())
                .decisionAt(wish.getDecisionAt())
                .build();
    }
}

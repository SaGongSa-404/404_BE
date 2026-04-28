package com.example._04_backend.domain.user.dto.response;

import com.example._04_backend.domain.wish.entity.SavedItem;
import com.example._04_backend.domain.wish.enums.ItemCategory;
import com.example._04_backend.domain.wish.enums.ItemStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class WishSummaryResponse {
    private UUID id;
    private String title;
    private Integer price;
    private String imageUrl;
    private ItemCategory category;
    private ItemStatus status;

    public static WishSummaryResponse of(SavedItem item) {
        return WishSummaryResponse.builder()
                .id(item.getId())
                .title(item.getTitle())
                .price(item.getListedPrice())
                .imageUrl(item.getImageUrl())
                .category(item.getCategory())
                .status(item.getStatus())
                .build();
    }
}

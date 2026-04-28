package com.example._04_backend.domain.wish.dto.response;

import com.example._04_backend.domain.wish.entity.SavedItem;
import com.example._04_backend.domain.wish.enums.ItemCategory;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class DeliberationInfoResponse {

    private WishInfo wish;
    private BudgetInfo budget;
    private MonthStats monthStats;
    private List<Question> questions;

    @Getter
    @Builder
    public static class WishInfo {
        private UUID id;
        private String title;
        private Integer price;
        private String imageUrl;
        private ItemCategory category;

        public static WishInfo of(SavedItem item) {
            return WishInfo.builder()
                    .id(item.getId())
                    .title(item.getTitle())
                    .price(item.getListedPrice())
                    .imageUrl(item.getImageUrl())
                    .category(item.getCategory())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class BudgetInfo {
        private Integer monthlyBudget;
        private Integer spentAmount;
        private Integer remainingBudget;
    }

    @Getter
    @Builder
    public static class MonthStats {
        private Integer spentAmount;
        private long irrationalCount;
        private Integer opportunityCost;
    }

    @Getter
    @Builder
    public static class Question {
        private int id;
        private String text;
    }
}

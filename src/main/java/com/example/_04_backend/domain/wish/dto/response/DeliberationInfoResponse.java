package com.example._04_backend.domain.wish.dto.response;

import com.example._04_backend.domain.wish.entity.Wish;
import com.example._04_backend.global.common.enums.Category;
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
        private Category category;

        public static WishInfo of(Wish wish) {
            return WishInfo.builder()
                    .id(wish.getId())
                    .title(wish.getTitle())
                    .price(wish.getPrice())
                    .imageUrl(wish.getImageUrl())
                    .category(wish.getCategory())
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

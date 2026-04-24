package com.example._04_backend.domain.wish.dto.response;

import com.example._04_backend.domain.wish.enums.WishStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class DeliberationResultResponse {

    private UUID wishId;
    private WishStatus decision;
    private int yesCount;
    private boolean warningTriggered;
    private MonthStats monthStats;

    @Getter
    @Builder
    public static class MonthStats {
        private Integer spentAmount;
        private long irrationalCount;
        private Integer opportunityCost;
    }
}

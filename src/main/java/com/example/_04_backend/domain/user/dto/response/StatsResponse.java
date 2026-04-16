package com.example._04_backend.domain.user.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StatsResponse {
    private String yearMonth;
    private Integer budgetAmount;
    private Integer spentAmount;
    private Integer restrainedAmount;
    private Double usageRate;
    private long boughtCount;
    private long restrainedCount;
    private List<CategoryStatResponse> byCategory;
}

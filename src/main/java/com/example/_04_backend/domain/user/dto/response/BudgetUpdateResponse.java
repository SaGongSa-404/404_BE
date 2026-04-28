package com.example._04_backend.domain.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BudgetUpdateResponse {
    private Integer monthlyBudget;
    private Object raccoonStatus; // 호환성 유지, 추후 제거
}

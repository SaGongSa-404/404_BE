package com.example._04_backend.domain.user.dto.response;

import com.example._04_backend.domain.user.enums.RaccoonStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BudgetUpdateResponse {
    private Integer monthlyBudget;
    private RaccoonStatus raccoonStatus;
}

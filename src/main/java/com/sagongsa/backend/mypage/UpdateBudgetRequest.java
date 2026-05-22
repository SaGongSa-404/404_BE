package com.sagongsa.backend.mypage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record UpdateBudgetRequest(
	@NotNull(message = "월 예산은 필수입니다.")
	@Min(value = 1, message = "월 예산은 1원 이상이어야 합니다.")
	Integer monthlyBudget
) {}

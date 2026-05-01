package com.sagongsa.backend.deliberation;

import java.math.BigDecimal;
import java.util.UUID;

public record DeliberationSummaryResponse(
	ItemSummary item,
	BudgetProjection budget,
	int similarCategorySpendAmount,
	String opportunityCostMessage
) {

	public record ItemSummary(
		UUID id,
		String title,
		String imageUrl,
		Integer listedPrice,
		String currencyCode,
		String category,
		String status
	) {
	}

	public record BudgetProjection(
		String yearMonth,
		int monthlyBudgetAmount,
		int spentAmount,
		int projectedSpentAmount,
		BigDecimal projectedUsageRate
	) {
	}
}

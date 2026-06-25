package com.sagongsa.backend.mypage;

import com.sagongsa.backend.domain.enums.ItemCategory;
import java.util.List;

record StatsResponse(
	String yearMonth,
	Integer budgetAmount,
	long spentAmount,
	long restrainedAmount,
	Double usageRate,
	long boughtCount,
	long restrainedCount,
	List<CategorySpendAmountResponse> categorySpendAmounts,
	Double rationalChoiceRate,
	long irrationalChoiceCount
) {}

record CategorySpendAmountResponse(
	ItemCategory category,
	long amount
) {}

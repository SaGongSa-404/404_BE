package com.sagongsa.backend.mypage;

record StatsResponse(
	String yearMonth,
	Integer budgetAmount,
	int spentAmount,
	int restrainedAmount,
	Double usageRate,
	long boughtCount,
	long restrainedCount
) {}

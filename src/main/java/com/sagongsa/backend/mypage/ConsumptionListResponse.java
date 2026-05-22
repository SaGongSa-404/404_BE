package com.sagongsa.backend.mypage;

import java.util.List;

public record ConsumptionListResponse(
	String month,
	List<ConsumptionRecord> items
) {}

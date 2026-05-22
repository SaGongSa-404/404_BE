package com.sagongsa.backend.mypage;

import java.util.List;

record AvailableMonthsResponse(
	List<String> months,
	String currentMonth
) {}

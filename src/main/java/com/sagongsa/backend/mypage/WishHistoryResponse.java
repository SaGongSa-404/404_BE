package com.sagongsa.backend.mypage;

import java.util.List;

record WishHistoryResponse(
	List<WishSummaryResponse> wishes,
	long total,
	int page,
	int size
) {}

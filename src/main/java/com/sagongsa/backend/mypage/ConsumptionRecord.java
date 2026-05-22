package com.sagongsa.backend.mypage;

import java.time.Instant;
import java.util.UUID;

public record ConsumptionRecord(
	UUID id,
	String itemTitle,
	Integer price,
	String result,
	Instant decidedAt,
	boolean isChanged,
	int changeCount
) {}

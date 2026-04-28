package com.sagongsa.backend.reflection;

import java.time.Instant;
import java.util.UUID;

public record PurchaseReflectionResponse(
	UUID id,
	UUID decisionId,
	UUID itemId,
	UUID reminderId,
	Integer satisfactionScore,
	String regretLevel,
	Boolean stillUsing,
	String reflectionNote,
	Instant reflectedAt
) {
}

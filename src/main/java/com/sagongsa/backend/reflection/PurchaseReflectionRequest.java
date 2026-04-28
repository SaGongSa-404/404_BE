package com.sagongsa.backend.reflection;

import java.util.UUID;

public record PurchaseReflectionRequest(
	UUID decisionId,
	Integer satisfactionScore,
	String regretLevel,
	Boolean stillUsing,
	String reflectionNote
) {
}

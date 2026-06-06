package com.sagongsa.backend.dev;

import java.util.Map;
import java.util.UUID;

public record QaRegretReminderScenarioResponse(
	UUID userId,
	String nickname,
	UUID budgetCycleId,
	String yearMonth,
	UUID itemId,
	UUID decisionId,
	UUID reminderId,
	Map<String, String> paths
) {
}

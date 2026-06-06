package com.sagongsa.backend.dev;

import java.util.Map;
import java.util.UUID;

public record QaUserScenarioResponse(
	UUID userId,
	String nickname,
	UUID budgetCycleId,
	String yearMonth,
	Map<String, String> paths
) {
}

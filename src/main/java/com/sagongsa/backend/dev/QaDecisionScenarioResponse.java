package com.sagongsa.backend.dev;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QaDecisionScenarioResponse(
	UUID userId,
	String nickname,
	UUID budgetCycleId,
	String yearMonth,
	List<UUID> itemIds,
	Map<String, UUID> decisionIds,
	Map<String, String> paths
) {
}

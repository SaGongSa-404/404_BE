package com.sagongsa.backend.dev;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QaBasicScenarioResponse(
	UUID userId,
	String nickname,
	UUID budgetCycleId,
	String yearMonth,
	List<UUID> itemIds,
	List<UUID> decisionIds,
	UUID deliberationItemId,
	List<UUID> notificationIds,
	Map<String, String> paths
) {
}

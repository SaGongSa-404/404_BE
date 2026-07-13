package com.sagongsa.backend.itemimport.job;

import com.sagongsa.backend.itemimport.item.ShoppingLinkImportResponse;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ShoppingImportJobResponse(
	UUID jobId,
	ShoppingImportJobStatus status,
	ShoppingLinkImportResponse result,
	ShoppingImportJobError error,
	int attemptCount,
	OffsetDateTime submittedAt,
	OffsetDateTime startedAt,
	OffsetDateTime completedAt
) {
}

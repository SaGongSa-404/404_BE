package com.sagongsa.backend.itemimport.job;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ShoppingImportJobAcceptedResponse(
	UUID jobId,
	ShoppingImportJobStatus status,
	OffsetDateTime submittedAt
) {
}

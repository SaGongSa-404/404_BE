package com.sagongsa.backend.itemimport.item;

import java.time.Instant;

public record ItemSourceMetadataDraft(
	String sourceDomain,
	String rawTitle,
	String rawDescription,
	String rawPriceText,
	String rawPayloadJson,
	Instant extractedAt,
	String extractionMethod
) {
}

package com.sagongsa.backend.itemimport.item;

import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemInputSource;
import com.sagongsa.backend.domain.enums.ItemStatus;

public record SavedItemDraft(
	ItemInputSource inputSource,
	String originalUrl,
	String normalizedUrl,
	String title,
	String brandName,
	String summary,
	String imageUrl,
	Integer listedPrice,
	String currencyCode,
	ItemCategory category,
	Double categoryConfidence,
	boolean categoryLockedByUser,
	ItemStatus status
) {
}

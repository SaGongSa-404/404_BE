package com.sagongsa.backend.itemimport.item;

import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemInputSource;

public record WishlistSaveDraft(
	ItemInputSource inputSource,
	String originalUrl,
	String normalizedUrl,
	String title,
	String imageUrl,
	Integer listedPrice,
	String currencyCode,
	ItemCategory category,
	Double categoryConfidence,
	boolean categoryLockedByUser,
	String sourceDomain,
	String rawTitle,
	String rawDescription,
	String rawPriceText,
	String rawPayloadJson
) {
}

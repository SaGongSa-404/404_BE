package com.sagongsa.backend.mypage;

import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemStatus;
import com.sagongsa.backend.domain.item.SavedItem;
import java.time.Instant;
import java.util.UUID;

record WishSummaryResponse(
	UUID id,
	UUID decisionId,
	String title,
	Integer price,
	String imageUrl,
	ItemCategory category,
	ItemStatus status,
	WishReflectionResponse reflection
) {
	static WishSummaryResponse of(SavedItem item) {
		return new WishSummaryResponse(
			item.getId(),
			null,
			item.getTitle(),
			item.getListedPrice(),
			item.getImageUrl(),
			item.getCategory(),
			item.getStatus(),
			null
		);
	}
}

record WishReflectionResponse(
	Integer satisfactionScore,
	String regretLevel,
	Boolean stillUsing,
	String reflectionNote,
	Instant reflectedAt
) {}

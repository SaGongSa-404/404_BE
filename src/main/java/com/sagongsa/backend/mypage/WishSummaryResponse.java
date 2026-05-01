package com.sagongsa.backend.mypage;

import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemStatus;
import com.sagongsa.backend.domain.item.SavedItem;
import java.util.UUID;

record WishSummaryResponse(
	UUID id,
	String title,
	Integer price,
	String imageUrl,
	ItemCategory category,
	ItemStatus status
) {
	static WishSummaryResponse of(SavedItem item) {
		return new WishSummaryResponse(
			item.getId(),
			item.getTitle(),
			item.getListedPrice(),
			item.getImageUrl(),
			item.getCategory(),
			item.getStatus()
		);
	}
}

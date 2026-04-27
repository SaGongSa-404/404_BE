package com.sagongsa.backend.itemimport.item;

import com.sagongsa.backend.domain.enums.ItemInputSource;

public record ShoppingLinkImportRequest(
	ItemInputSource inputSource,
	String url,
	String title,
	String brandName,
	Integer price,
	String imageUrl
) {
}

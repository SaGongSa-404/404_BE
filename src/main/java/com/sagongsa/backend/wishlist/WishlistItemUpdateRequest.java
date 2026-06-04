package com.sagongsa.backend.wishlist;

import io.swagger.v3.oas.annotations.media.Schema;

public record WishlistItemUpdateRequest(
	@Schema(description = "Original item URL. Optional. For DIRECT_INPUT items, blank or null clears the saved URL. For SHARE items, omit this field because URL changes are rejected.")
	String originalUrl,
	@Schema(description = "Canonical item URL. Optional. If omitted, originalUrl is normalized. For DIRECT_INPUT items, blank or null clears the saved normalized URL. For SHARE items, omit this field because URL changes are rejected.")
	String normalizedUrl,
	@Schema(description = "Item title. Required; null or blank returns 400.", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
	String title,
	@Schema(description = "Listed price. Optional; null clears the saved price.", minimum = "0")
	Integer listedPrice,
	@Schema(
		description = "Wishlist category. Required; null, blank, or unsupported values return 400.",
		requiredMode = Schema.RequiredMode.REQUIRED,
		allowableValues = {"FASHION", "BEAUTY", "DIGITAL", "LIVING", "FOOD", "HOBBY", "SUBSCRIPTION", "ETC"}
	)
	String category
) {
}

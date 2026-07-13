package com.sagongsa.backend.wishlist;

public record OpportunityCostResponse(
	int originalPrice,
	String sourceCategory,
	OpportunityCostResult result
) {

	public record OpportunityCostResult(
		String itemId,
		String targetCategory,
		int calculatedCount,
		String displayTitle,
		String displayMessage
	) {
	}
}

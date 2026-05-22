package com.sagongsa.backend.wishlist;

import java.util.List;

public record WishlistItemPageResponse(
	List<WishlistItemSummaryResponse> items,
	String nextCursor,
	boolean hasMore
) {
}

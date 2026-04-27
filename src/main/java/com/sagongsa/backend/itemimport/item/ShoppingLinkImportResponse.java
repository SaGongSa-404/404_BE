package com.sagongsa.backend.itemimport.item;

import java.util.List;

public record ShoppingLinkImportResponse(
	String retrievalStatus,
	SavedItemDraft item,
	ItemSourceMetadataDraft sourceMetadata,
	WishlistSaveDraft saveRequest,
	List<String> warnings
) {
}

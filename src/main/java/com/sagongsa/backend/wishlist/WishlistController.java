package com.sagongsa.backend.wishlist;

import com.sagongsa.backend.auth.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wishlist/items")
@Tag(name = "Wishlist", description = "Saved wishlist item create, list, detail, update, category update, and delete APIs")
public class WishlistController {

	private final WishlistService wishlistService;

	public WishlistController(WishlistService wishlistService) {
		this.wishlistService = wishlistService;
	}

	@PostMapping
	@Operation(
		summary = "Create wishlist item",
		description = "Saves an imported or directly entered item into the authenticated user's wishlist.",
		responses = {
			@ApiResponse(responseCode = "201", description = "Wishlist item created"),
			@ApiResponse(responseCode = "400", description = "Invalid wishlist payload"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "409", description = "Same normalized URL is already saved")
		}
	)
	public ResponseEntity<WishlistItemResponse> create(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@RequestBody WishlistItemCreateRequest request
	) {
		WishlistItemResponse response = wishlistService.create(userId, request);
		return ResponseEntity.created(URI.create("/api/v1/wishlist/items/" + response.id())).body(response);
	}

	@GetMapping
	@Operation(
		summary = "List wishlist items",
		description = "Returns the authenticated user's saved wishlist items with optional category filtering and cursor pagination.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Wishlist items returned"),
			@ApiResponse(responseCode = "400", description = "Invalid pagination cursor or limit"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication")
		}
	)
	public WishlistItemPageResponse list(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@RequestParam(required = false) String category,
		@RequestParam(required = false) Integer limit,
		@RequestParam(required = false) String cursor
	) {
		return wishlistService.list(userId, category, limit, parseCursor(cursor));
	}

	@GetMapping("/{itemId}")
	@Operation(
		summary = "Get wishlist item",
		description = "Returns one saved wishlist item owned by the authenticated user.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Wishlist item returned"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "403", description = "Item belongs to another user"),
			@ApiResponse(responseCode = "404", description = "Wishlist item does not exist")
		}
	)
	public WishlistItemResponse get(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@PathVariable UUID itemId
	) {
		return wishlistService.get(userId, itemId);
	}

	@PatchMapping("/{itemId}")
	@Operation(
		summary = "Update wishlist item",
		description = "Replaces the editable wishlist item fields. title and category are required. listedPrice is optional and null clears the saved price. Direct input items can update URL fields, and blank or null URL fields clear the saved URL. Shared-link item URLs cannot be changed.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Wishlist item updated"),
			@ApiResponse(responseCode = "400", description = "Invalid wishlist update payload"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "403", description = "Item belongs to another user"),
			@ApiResponse(responseCode = "404", description = "Wishlist item does not exist"),
			@ApiResponse(responseCode = "409", description = "Same normalized URL is already saved")
		}
	)
	public WishlistItemResponse update(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@PathVariable UUID itemId,
		@RequestBody WishlistItemUpdateRequest request
	) {
		return wishlistService.update(userId, itemId, request);
	}

	@PatchMapping("/{itemId}/category")
	@Operation(
		summary = "Update wishlist category",
		description = "Changes an item category and marks the category as user locked.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Category updated"),
			@ApiResponse(responseCode = "400", description = "Invalid category"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "403", description = "Item belongs to another user"),
			@ApiResponse(responseCode = "404", description = "Wishlist item does not exist")
		}
	)
	public WishlistItemResponse updateCategory(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@PathVariable UUID itemId,
		@RequestBody WishlistCategoryUpdateRequest request
	) {
		return wishlistService.updateCategory(userId, itemId, request);
	}

	@DeleteMapping("/{itemId}")
	@Operation(
		summary = "Delete wishlist item",
		description = "Drops a saved wishlist item from the user's active wishlist.",
		responses = {
			@ApiResponse(responseCode = "204", description = "Wishlist item deleted"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "403", description = "Item belongs to another user"),
			@ApiResponse(responseCode = "404", description = "Wishlist item does not exist")
		}
	)
	public ResponseEntity<Void> delete(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@PathVariable UUID itemId
	) {
		wishlistService.drop(userId, itemId);
		return ResponseEntity.noContent().build();
	}

	private WishlistCursor parseCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		return WishlistCursor.parse(cursor);
	}
}

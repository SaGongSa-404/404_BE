package com.sagongsa.backend.wishlist;

import com.sagongsa.backend.auth.CurrentUserId;
import java.net.URI;
import java.util.List;
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
public class WishlistController {

	private final WishlistService wishlistService;

	public WishlistController(WishlistService wishlistService) {
		this.wishlistService = wishlistService;
	}

	@PostMapping
	public ResponseEntity<WishlistItemResponse> create(
		@CurrentUserId UUID userId,
		@RequestBody WishlistItemCreateRequest request
	) {
		WishlistItemResponse response = wishlistService.create(userId, request);
		return ResponseEntity.created(URI.create("/api/v1/wishlist/items/" + response.id())).body(response);
	}

	@GetMapping
	public List<WishlistItemResponse> list(
		@CurrentUserId UUID userId,
		@RequestParam(required = false) String category
	) {
		return wishlistService.list(userId, category);
	}

	@GetMapping("/{itemId}")
	public WishlistItemResponse get(
		@CurrentUserId UUID userId,
		@PathVariable UUID itemId
	) {
		return wishlistService.get(userId, itemId);
	}

	@PatchMapping("/{itemId}/category")
	public WishlistItemResponse updateCategory(
		@CurrentUserId UUID userId,
		@PathVariable UUID itemId,
		@RequestBody WishlistCategoryUpdateRequest request
	) {
		return wishlistService.updateCategory(userId, itemId, request);
	}

	@DeleteMapping("/{itemId}")
	public ResponseEntity<Void> delete(
		@CurrentUserId UUID userId,
		@PathVariable UUID itemId
	) {
		wishlistService.drop(userId, itemId);
		return ResponseEntity.noContent().build();
	}
}

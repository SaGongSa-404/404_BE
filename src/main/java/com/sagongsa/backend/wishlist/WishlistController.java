package com.sagongsa.backend.wishlist;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wishlist/items")
public class WishlistController {

	private static final String USER_ID_HEADER = "X-User-Id";

	private final WishlistService wishlistService;

	public WishlistController(WishlistService wishlistService) {
		this.wishlistService = wishlistService;
	}

	@PostMapping
	public ResponseEntity<WishlistItemResponse> create(
		@RequestHeader(value = USER_ID_HEADER, required = false) String rawUserId,
		@RequestBody WishlistItemCreateRequest request
	) {
		UUID userId = parseUserId(rawUserId);
		WishlistItemResponse response = wishlistService.create(userId, request);
		return ResponseEntity.created(URI.create("/api/v1/wishlist/items/" + response.id())).body(response);
	}

	@GetMapping
	public List<WishlistItemResponse> list(
		@RequestHeader(value = USER_ID_HEADER, required = false) String rawUserId,
		@RequestParam(required = false) String category
	) {
		return wishlistService.list(parseUserId(rawUserId), category);
	}

	@PatchMapping("/{itemId}/category")
	public WishlistItemResponse updateCategory(
		@RequestHeader(value = USER_ID_HEADER, required = false) String rawUserId,
		@PathVariable UUID itemId,
		@RequestBody WishlistCategoryUpdateRequest request
	) {
		return wishlistService.updateCategory(parseUserId(rawUserId), itemId, request);
	}

	@DeleteMapping("/{itemId}")
	public ResponseEntity<Void> delete(
		@RequestHeader(value = USER_ID_HEADER, required = false) String rawUserId,
		@PathVariable UUID itemId
	) {
		wishlistService.drop(parseUserId(rawUserId), itemId);
		return ResponseEntity.noContent().build();
	}

	private UUID parseUserId(String rawUserId) {
		if (!StringUtils.hasText(rawUserId)) {
			throw new BadRequestException(USER_ID_HEADER + " header is required.");
		}

		try {
			return UUID.fromString(rawUserId.trim());
		}
		catch (IllegalArgumentException exception) {
			throw new BadRequestException(USER_ID_HEADER + " header must be a UUID.");
		}
	}
}

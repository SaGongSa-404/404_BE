package com.sagongsa.backend.mypage;

import com.sagongsa.backend.auth.CurrentUserId;
import com.sagongsa.backend.domain.enums.ItemStatus;
import com.sagongsa.backend.social.PostListResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users/me")
public class MypageController {

	private final MypageService mypageService;

	public MypageController(MypageService mypageService) {
		this.mypageService = mypageService;
	}

	@GetMapping
	public ResponseEntity<MyProfileResponse> getMyProfile(@CurrentUserId UUID userId) {
		return ResponseEntity.ok(mypageService.getMyProfile(userId));
	}

	@PatchMapping("/profile")
	public ResponseEntity<MyProfileResponse> updateProfile(
		@CurrentUserId UUID userId,
		@Valid @RequestBody UpdateProfileRequest request) {
		return ResponseEntity.ok(mypageService.updateProfile(userId, request));
	}

	@PatchMapping("/budget")
	public ResponseEntity<MypageService.BudgetUpdateResponse> updateBudget(
		@CurrentUserId UUID userId,
		@Valid @RequestBody UpdateBudgetRequest request) {
		return ResponseEntity.ok(mypageService.updateBudget(userId, request));
	}

	@GetMapping("/notification-settings")
	public ResponseEntity<MypageService.NotificationSettingsResponse> getNotificationSettings(
		@CurrentUserId UUID userId) {
		return ResponseEntity.ok(mypageService.getNotificationSettings(userId));
	}

	@PatchMapping("/notification-settings")
	public ResponseEntity<MypageService.NotificationSettingsResponse> updateNotificationSettings(
		@CurrentUserId UUID userId,
		@Valid @RequestBody NotificationSettingsRequest request) {
		return ResponseEntity.ok(mypageService.updateNotificationSettings(userId, request));
	}

	@DeleteMapping
	public ResponseEntity<Void> deleteAccount(@CurrentUserId UUID userId) {
		mypageService.deleteAccount(userId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/stats/months")
	public ResponseEntity<AvailableMonthsResponse> getAvailableMonths(@CurrentUserId UUID userId) {
		return ResponseEntity.ok(mypageService.getAvailableMonths(userId));
	}

	@GetMapping("/stats")
	public ResponseEntity<StatsResponse> getStats(
		@CurrentUserId UUID userId,
		@RequestParam(required = false) String yearMonth) {
		return ResponseEntity.ok(mypageService.getStats(userId, yearMonth));
	}

	@GetMapping("/wishes/history")
	public ResponseEntity<WishHistoryResponse> getWishHistory(
		@CurrentUserId UUID userId,
		@RequestParam(required = false) ItemStatus status,
		@RequestParam(required = false) String yearMonth,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ResponseEntity.ok(mypageService.getWishHistory(userId, status, yearMonth, page, size));
	}

	@GetMapping("/posts")
	public ResponseEntity<PostListResponse> getMyPosts(
		@CurrentUserId UUID userId,
		@RequestParam(required = false) Instant cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ResponseEntity.ok(mypageService.getMyPosts(userId, cursor, size));
	}

	@GetMapping("/votes")
	public ResponseEntity<PostListResponse> getMyVotedPosts(
		@CurrentUserId UUID userId,
		@RequestParam(required = false) Instant cursor,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ResponseEntity.ok(mypageService.getMyVotedPosts(userId, cursor, size));
	}
}

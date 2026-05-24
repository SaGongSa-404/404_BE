package com.sagongsa.backend.mypage;

import com.sagongsa.backend.domain.auth.SocialAccount;
import com.sagongsa.backend.domain.auth.SocialAccountRepository;
import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.budget.BudgetCycle;
import com.sagongsa.backend.domain.budget.BudgetCycleRepository;
import com.sagongsa.backend.domain.enums.ItemStatus;
import com.sagongsa.backend.domain.item.SavedItem;
import com.sagongsa.backend.domain.item.SavedItemRepository;
import com.sagongsa.backend.domain.social.FeedPostRepository;
import com.sagongsa.backend.domain.social.PostCommentRepository;
import com.sagongsa.backend.domain.social.PostVote;
import com.sagongsa.backend.domain.social.PostVoteRepository;
import com.sagongsa.backend.domain.user.UserProfile;
import com.sagongsa.backend.domain.user.UserProfileRepository;
import com.sagongsa.backend.social.PostListResponse;
import com.sagongsa.backend.social.PostResponse;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class MypageService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final UserAccountRepository userAccountRepository;
	private final UserProfileRepository userProfileRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final FeedPostRepository feedPostRepository;
	private final PostVoteRepository postVoteRepository;
	private final PostCommentRepository postCommentRepository;
	private final SavedItemRepository savedItemRepository;
	private final BudgetCycleRepository budgetCycleRepository;
	private final JdbcTemplate jdbcTemplate;

	MypageService(UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		SocialAccountRepository socialAccountRepository,
		FeedPostRepository feedPostRepository,
		PostVoteRepository postVoteRepository,
		PostCommentRepository postCommentRepository,
		SavedItemRepository savedItemRepository,
		BudgetCycleRepository budgetCycleRepository,
		JdbcTemplate jdbcTemplate) {
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.socialAccountRepository = socialAccountRepository;
		this.feedPostRepository = feedPostRepository;
		this.postVoteRepository = postVoteRepository;
		this.postCommentRepository = postCommentRepository;
		this.savedItemRepository = savedItemRepository;
		this.budgetCycleRepository = budgetCycleRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	MyProfileResponse getMyProfile(UUID userId) {
		UserAccount user = findUserOrThrow(userId);
		UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
		SocialAccount social = socialAccountRepository.findByUserId(userId).orElse(null);
		long postCount = feedPostRepository.countByUserIdAndDeletedAtIsNull(userId);
		return MyProfileResponse.of(user, profile, social, postCount);
	}

	@Transactional
	MyProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
		UserAccount user = findUserOrThrow(userId);
		UserProfile profile = userProfileRepository.findByUserId(userId)
			.orElseGet(() -> userProfileRepository.save(
				UserProfile.create(user,
					request.nickname() != null ? request.nickname() : "사용자",
					request.raccoonName() != null ? request.raccoonName() : "너구리")));
		profile.updateProfile(request.nickname(), request.raccoonName());
		SocialAccount social = socialAccountRepository.findByUserId(userId).orElse(null);
		long postCount = feedPostRepository.countByUserIdAndDeletedAtIsNull(userId);
		return MyProfileResponse.of(user, profile, social, postCount);
	}

	@Transactional
	record BudgetUpdateResponse(Integer monthlyBudget) {}

	@Transactional
	BudgetUpdateResponse updateBudget(UUID userId, UpdateBudgetRequest request) {
		UserAccount user = findUserOrThrow(userId);
		String yearMonth = YearMonth.now(KST).toString();
		BudgetCycle cycle = budgetCycleRepository.findByUserIdAndYearMonth(userId, yearMonth)
			.orElseGet(() -> budgetCycleRepository.save(
				BudgetCycle.create(user, yearMonth, request.monthlyBudget())));
		cycle.updateBudgetAmount(request.monthlyBudget());
		return new BudgetUpdateResponse(request.monthlyBudget());
	}

	record NotificationSettingsResponse(boolean notificationEnabled) {}

	NotificationSettingsResponse getNotificationSettings(UUID userId) {
		findUserOrThrow(userId);
		UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
		return new NotificationSettingsResponse(profile == null || profile.isNotificationEnabled());
	}

	@Transactional
	NotificationSettingsResponse updateNotificationSettings(UUID userId, NotificationSettingsRequest request) {
		UserAccount user = findUserOrThrow(userId);
		UserProfile profile = userProfileRepository.findByUserId(userId)
			.orElseGet(() -> userProfileRepository.save(
				UserProfile.create(user, "사용자", "너구리")));
		profile.updateNotificationEnabled(request.notificationEnabled());
		return new NotificationSettingsResponse(request.notificationEnabled());
	}

	@Transactional
	void deleteAccount(UUID userId) {
		UserAccount user = findUserOrThrow(userId);

		// Social: votes/comments by user and on user's posts
		postVoteRepository.deleteByUserId(userId);
		postCommentRepository.deleteByUserId(userId);
		postVoteRepository.deleteByPostUserId(userId);
		postCommentRepository.deleteByPostUserId(userId);
		feedPostRepository.softDeleteByUserId(userId);
		// Unlink feed posts from saved items (item_id is nullable)
		jdbcTemplate.update("UPDATE feed_posts SET item_id = NULL, decision_id = NULL WHERE user_id = ?", userId);

		// Decision history — delete in FK dependency order before saved_items
		jdbcTemplate.update("""
			DELETE FROM self_check_answers WHERE response_set_id IN (
				SELECT id FROM self_check_response_sets
				WHERE decision_id IN (SELECT id FROM purchase_decisions WHERE user_id = ?))""", userId);
		jdbcTemplate.update("DELETE FROM self_check_response_sets WHERE decision_id IN (SELECT id FROM purchase_decisions WHERE user_id = ?)", userId);
		jdbcTemplate.update("DELETE FROM purchase_decision_change_logs WHERE decision_id IN (SELECT id FROM purchase_decisions WHERE user_id = ?)", userId);
		jdbcTemplate.update("DELETE FROM purchase_reflections WHERE decision_id IN (SELECT id FROM purchase_decisions WHERE user_id = ?)", userId);
		jdbcTemplate.update("DELETE FROM mascot_state_events WHERE user_id = ?", userId);
		jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
		jdbcTemplate.update("DELETE FROM reminder_schedules WHERE user_id = ?", userId);
		jdbcTemplate.update("DELETE FROM purchase_decisions WHERE user_id = ?", userId);
		jdbcTemplate.update("DELETE FROM item_source_metadata WHERE item_id IN (SELECT id FROM saved_items WHERE user_id = ?)", userId);

		savedItemRepository.deleteByUserId(userId);
		budgetCycleRepository.deleteByUserId(userId);
		userProfileRepository.deleteByUserId(userId);
		user.withdraw();
	}

	AvailableMonthsResponse getAvailableMonths(UUID userId) {
		findUserOrThrow(userId);
		Set<String> months = new LinkedHashSet<>(budgetCycleRepository.findYearMonthsByUserId(userId));
		String current = YearMonth.now(KST).toString();
		months.add(current);
		List<String> sorted = new ArrayList<>(months);
		sorted.sort((a, b) -> b.compareTo(a));
		return new AvailableMonthsResponse(sorted, current);
	}

	StatsResponse getStats(UUID userId, String yearMonth) {
		if (yearMonth == null || yearMonth.isBlank()) {
			yearMonth = YearMonth.now(KST).toString();
		}
		findUserOrThrow(userId);
		YearMonth ym = YearMonth.parse(yearMonth);
		Instant from = ym.atDay(1).atStartOfDay(KST).toInstant();
		Instant to = ym.atEndOfMonth().plusDays(1).atStartOfDay(KST).toInstant();

		BudgetCycle budget = budgetCycleRepository.findByUserIdAndYearMonth(userId, yearMonth).orElse(null);
		Integer budgetAmount = budget != null ? budget.getMonthlyBudgetAmount() : null;
		Integer spent = savedItemRepository.sumPriceByUserIdAndStatusBetween(userId, ItemStatus.GO, from, to);
		Integer restrained = savedItemRepository.sumPriceByUserIdAndStatusBetween(userId, ItemStatus.STOP, from, to);
		long boughtCount = savedItemRepository.countByUserIdAndStatusBetween(userId, ItemStatus.GO, from, to);
		long restrainedCount = savedItemRepository.countByUserIdAndStatusBetween(userId, ItemStatus.STOP, from, to);

		Double usageRate = null;
		int spentVal = spent != null ? spent : 0;
		if (budgetAmount != null && budgetAmount > 0) {
			usageRate = Math.round((double) spentVal / budgetAmount * 1000.0) / 10.0;
		}

		return new StatsResponse(yearMonth, budgetAmount, spentVal,
			restrained != null ? restrained : 0, usageRate, boughtCount, restrainedCount);
	}

	WishHistoryResponse getWishHistory(UUID userId, ItemStatus status, String yearMonth, int page, int size) {
		if (yearMonth == null || yearMonth.isBlank()) {
			yearMonth = YearMonth.now(KST).toString();
		}
		YearMonth ym = YearMonth.parse(yearMonth);
		Instant from = ym.atDay(1).atStartOfDay(KST).toInstant();
		Instant to = ym.atEndOfMonth().plusDays(1).atStartOfDay(KST).toInstant();

		List<SavedItem> items = savedItemRepository.findByUserIdAndStatusBetween(
			userId, status, from, to, PageRequest.of(page, size));

		long total = (status != null)
			? savedItemRepository.countByUserIdAndStatusBetween(userId, status, from, to)
			: savedItemRepository.countByUserIdAndStatusBetween(userId, ItemStatus.GO, from, to)
			+ savedItemRepository.countByUserIdAndStatusBetween(userId, ItemStatus.STOP, from, to);

		List<WishSummaryResponse> wishes = items.stream().map(WishSummaryResponse::of).toList();
		return new WishHistoryResponse(wishes, total, page, size);
	}

	PostListResponse getMyPosts(UUID userId, Instant cursor, int size) {
		PageRequest pageable = PageRequest.of(0, size + 1);
		var posts = cursor != null
			? feedPostRepository.findByUserIdVisibleBefore(userId, cursor, pageable)
			: feedPostRepository.findByUserIdVisible(userId, pageable);

		boolean hasMore = posts.size() > size;
		if (hasMore) posts = posts.subList(0, size);

		String myNickname = userProfileRepository.findByUserId(userId).isPresent()
			? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;

		var items = posts.stream().map(post -> {
			long cc = postCommentRepository.countByPostIdAndDeletedAtIsNull(post.getId());
			var myVote = postVoteRepository.findByPostIdAndUserId(post.getId(), userId)
				.filter(PostVote::isActive).map(PostVote::getVoteType).orElse(null);
			return PostResponse.of(post, myNickname, cc, myVote);
		}).toList();

		Instant nextCursor = hasMore && !posts.isEmpty() ? posts.get(posts.size() - 1).getCreatedAt() : null;
		return new PostListResponse(items, nextCursor, hasMore);
	}

	PostListResponse getMyVotedPosts(UUID userId, Instant cursor, int size) {
		PageRequest pageable = PageRequest.of(0, size + 1);
		var votes = cursor != null
			? postVoteRepository.findActiveByUserIdBefore(userId, cursor, pageable)
			: postVoteRepository.findActiveByUserId(userId, pageable);

		boolean hasMore = votes.size() > size;
		if (hasMore) votes = votes.subList(0, size);

		List<UUID> authorIds = votes.stream()
			.map(v -> v.getPost().getUser().getId()).distinct().toList();
		Set<UUID> existingProfileIds = new HashSet<>(userProfileRepository.findExistingProfileUserIds(authorIds));

		var items = votes.stream().map(vote -> {
			var post = vote.getPost();
			String authorNickname = existingProfileIds.contains(post.getUser().getId())
				? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;
			long cc = postCommentRepository.countByPostIdAndDeletedAtIsNull(post.getId());
			return PostResponse.of(post, authorNickname, cc, vote.getVoteType());
		}).toList();

		Instant nextCursor = hasMore && !votes.isEmpty() ? votes.get(votes.size() - 1).getCreatedAt() : null;
		return new PostListResponse(items, nextCursor, hasMore);
	}

	private UserAccount findUserOrThrow(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new MypageNotFoundException("사용자를 찾을 수 없습니다."));
	}
}

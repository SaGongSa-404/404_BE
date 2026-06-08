package com.sagongsa.backend.mypage;

import com.sagongsa.backend.domain.auth.SocialAccount;
import com.sagongsa.backend.domain.auth.SocialAccountRepository;
import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.budget.BudgetCycle;
import com.sagongsa.backend.domain.budget.BudgetCycleRepository;
import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemStatus;
import com.sagongsa.backend.domain.enums.ModerationStatus;
import com.sagongsa.backend.domain.item.SavedItemRepository;
import com.sagongsa.backend.domain.notification.DevicePushTokenRepository;
import com.sagongsa.backend.domain.social.FeedPostRepository;
import com.sagongsa.backend.domain.social.PostCommentCount;
import com.sagongsa.backend.domain.social.PostCommentRepository;
import com.sagongsa.backend.domain.social.PostVote;
import com.sagongsa.backend.domain.social.PostVoteRepository;
import com.sagongsa.backend.domain.user.UserProfile;
import com.sagongsa.backend.domain.user.UserProfileRepository;
import com.sagongsa.backend.social.BlockService;
import com.sagongsa.backend.social.PostListResponse;
import com.sagongsa.backend.social.PostResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
	private final DevicePushTokenRepository devicePushTokenRepository;
	private final JdbcTemplate jdbcTemplate;
	private final BlockService blockService;

	MypageService(UserAccountRepository userAccountRepository,
		UserProfileRepository userProfileRepository,
		SocialAccountRepository socialAccountRepository,
		FeedPostRepository feedPostRepository,
		PostVoteRepository postVoteRepository,
		PostCommentRepository postCommentRepository,
		SavedItemRepository savedItemRepository,
		BudgetCycleRepository budgetCycleRepository,
		DevicePushTokenRepository devicePushTokenRepository,
		JdbcTemplate jdbcTemplate,
		BlockService blockService) {
		this.userAccountRepository = userAccountRepository;
		this.userProfileRepository = userProfileRepository;
		this.socialAccountRepository = socialAccountRepository;
		this.feedPostRepository = feedPostRepository;
		this.postVoteRepository = postVoteRepository;
		this.postCommentRepository = postCommentRepository;
		this.savedItemRepository = savedItemRepository;
		this.budgetCycleRepository = budgetCycleRepository;
		this.devicePushTokenRepository = devicePushTokenRepository;
		this.jdbcTemplate = jdbcTemplate;
		this.blockService = blockService;
	}

	MyProfileResponse getMyProfile(UUID userId) {
		UserAccount user = findUserOrThrow(userId);
		UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
		SocialAccount social = socialAccountRepository.findByUserId(userId).orElse(null);
		long postCount = feedPostRepository.countByUserIdAndDeletedAtIsNullAndModerationStatus(userId, ModerationStatus.ACTIVE);
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
		long postCount = feedPostRepository.countByUserIdAndDeletedAtIsNullAndModerationStatus(userId, ModerationStatus.ACTIVE);
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

		devicePushTokenRepository.deleteByUserId(userId);
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
		StatsAggregation stats = getStatsAggregation(userId, from, to);
		List<CategorySpendAmountResponse> categorySpendAmounts = getCategorySpendAmounts(userId, from, to);

		Double usageRate = null;
		if (budgetAmount != null && budgetAmount > 0) {
			usageRate = roundRate((double) stats.spentAmount() / budgetAmount * 100.0);
		}

		return new StatsResponse(
			yearMonth,
			budgetAmount,
			stats.spentAmount(),
			stats.restrainedAmount(),
			usageRate,
			stats.boughtCount(),
			stats.restrainedCount(),
			categorySpendAmounts,
			stats.rationalChoiceRate(),
			stats.irrationalChoiceCount()
		);
	}

	WishHistoryResponse getWishHistory(UUID userId, ItemStatus status, String yearMonth, int page, int size) {
		if (yearMonth == null || yearMonth.isBlank()) {
			yearMonth = YearMonth.now(KST).toString();
		}
		YearMonth ym = YearMonth.parse(yearMonth);
		Instant from = ym.atDay(1).atStartOfDay(KST).toInstant();
		Instant to = ym.atEndOfMonth().plusDays(1).atStartOfDay(KST).toInstant();

		List<WishSummaryResponse> wishes = getWishSummaries(userId, status, from, to, page, size);
		long total = countWishSummaries(userId, status, from, to);
		return new WishHistoryResponse(wishes, total, page, size);
	}

	private List<WishSummaryResponse> getWishSummaries(
		UUID userId, ItemStatus status, Instant from, Instant to, int page, int size) {
		if (status == null) {
			return jdbcTemplate.query(
				"""
				SELECT si.id,
				       pd.id AS decision_id,
				       si.title,
				       COALESCE(pd.final_price, si.listed_price) AS price,
				       si.image_url,
				       si.category,
				       si.status,
				       pr.satisfaction_score,
				       pr.regret_level,
				       pr.still_using,
				       pr.reflection_note,
				       pr.reflected_at
				FROM purchase_decisions pd
				JOIN saved_items si ON si.id = pd.item_id
				LEFT JOIN purchase_reflections pr ON pr.decision_id = pd.id
				WHERE pd.user_id = ?
				  AND pd.decided_at >= ?
				  AND pd.decided_at < ?
				ORDER BY pd.decided_at DESC
				LIMIT ? OFFSET ?
				""",
				this::mapWishSummary,
				userId,
				Timestamp.from(from),
				Timestamp.from(to),
				size,
				page * size
			);
		}

		return jdbcTemplate.query(
			"""
			SELECT si.id,
			       pd.id AS decision_id,
			       si.title,
			       COALESCE(pd.final_price, si.listed_price) AS price,
			       si.image_url,
			       si.category,
			       si.status,
			       pr.satisfaction_score,
			       pr.regret_level,
			       pr.still_using,
			       pr.reflection_note,
			       pr.reflected_at
			FROM purchase_decisions pd
			JOIN saved_items si ON si.id = pd.item_id
			LEFT JOIN purchase_reflections pr ON pr.decision_id = pd.id
			WHERE pd.user_id = ?
			  AND si.status = ?
			  AND pd.decided_at >= ?
			  AND pd.decided_at < ?
			ORDER BY pd.decided_at DESC
			LIMIT ? OFFSET ?
			""",
			this::mapWishSummary,
			userId,
			status.name(),
			Timestamp.from(from),
			Timestamp.from(to),
			size,
			page * size
		);
	}

	private long countWishSummaries(UUID userId, ItemStatus status, Instant from, Instant to) {
		if (status == null) {
			return jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*)
				FROM purchase_decisions pd
				JOIN saved_items si ON si.id = pd.item_id
				WHERE pd.user_id = ?
				  AND pd.decided_at >= ?
				  AND pd.decided_at < ?
				""",
				Long.class,
				userId,
				Timestamp.from(from),
				Timestamp.from(to)
			);
		}

		return jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM purchase_decisions pd
			JOIN saved_items si ON si.id = pd.item_id
			WHERE pd.user_id = ?
			  AND si.status = ?
			  AND pd.decided_at >= ?
			  AND pd.decided_at < ?
			""",
			Long.class,
			userId,
			status.name(),
			Timestamp.from(from),
			Timestamp.from(to)
		);
	}

	private StatsAggregation getStatsAggregation(UUID userId, Instant from, Instant to) {
		return jdbcTemplate.queryForObject(
			"""
			SELECT COALESCE(SUM(CASE WHEN pd.result = 'GO'
			           THEN COALESCE(pd.final_price, si.listed_price, 0) ELSE 0 END), 0)::integer AS spent_amount,
			       COALESCE(SUM(CASE WHEN pd.result = 'STOP'
			           THEN COALESCE(si.listed_price, 0) ELSE 0 END), 0)::integer AS restrained_amount,
			       COUNT(*) FILTER (WHERE pd.result = 'GO') AS bought_count,
			       COUNT(*) FILTER (WHERE pd.result = 'STOP') AS restrained_count,
			       COUNT(*) AS decision_count,
			       COUNT(*) FILTER (WHERE pd.rationality_result = 'RATIONAL') AS rational_count,
			       COUNT(*) FILTER (WHERE pd.rationality_result = 'IRRATIONAL') AS irrational_count
			FROM purchase_decisions pd
			JOIN saved_items si ON si.id = pd.item_id
			WHERE pd.user_id = ?
			  AND pd.decided_at >= ?
			  AND pd.decided_at < ?
			""",
			(rs, rowNum) -> {
				long decisionCount = rs.getLong("decision_count");
				Double rationalChoiceRate = decisionCount == 0
					? null
					: roundRate((double) rs.getLong("rational_count") / decisionCount * 100.0);
				return new StatsAggregation(
					rs.getInt("spent_amount"),
					rs.getInt("restrained_amount"),
					rs.getLong("bought_count"),
					rs.getLong("restrained_count"),
					rationalChoiceRate,
					rs.getLong("irrational_count")
				);
			},
			userId,
			Timestamp.from(from),
			Timestamp.from(to)
		);
	}

	private List<CategorySpendAmountResponse> getCategorySpendAmounts(UUID userId, Instant from, Instant to) {
		return jdbcTemplate.query(
			"""
			SELECT si.category,
			       COALESCE(SUM(COALESCE(pd.final_price, si.listed_price, 0)), 0)::integer AS amount
			FROM purchase_decisions pd
			JOIN saved_items si ON si.id = pd.item_id
			WHERE pd.user_id = ?
			  AND pd.result = 'GO'
			  AND pd.decided_at >= ?
			  AND pd.decided_at < ?
			GROUP BY si.category
			ORDER BY amount DESC, si.category ASC
			""",
			(rs, rowNum) -> new CategorySpendAmountResponse(
				ItemCategory.valueOf(rs.getString("category")),
				rs.getInt("amount")
			),
			userId,
			Timestamp.from(from),
			Timestamp.from(to)
		);
	}

	private WishSummaryResponse mapWishSummary(ResultSet rs, int rowNum) throws SQLException {
		return new WishSummaryResponse(
			rs.getObject("id", UUID.class),
			rs.getObject("decision_id", UUID.class),
			rs.getString("title"),
			nullableInt(rs, "price"),
			rs.getString("image_url"),
			ItemCategory.valueOf(rs.getString("category")),
			ItemStatus.valueOf(rs.getString("status")),
			mapWishReflection(rs)
		);
	}

	private WishReflectionResponse mapWishReflection(ResultSet rs) throws SQLException {
		String regretLevel = rs.getString("regret_level");
		if (regretLevel == null) {
			return null;
		}
		return new WishReflectionResponse(
			nullableInt(rs, "satisfaction_score"),
			regretLevel,
			nullableBoolean(rs, "still_using"),
			rs.getString("reflection_note"),
			toInstant(rs, "reflected_at")
		);
	}

	private Integer nullableInt(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	private Boolean nullableBoolean(ResultSet rs, String column) throws SQLException {
		boolean value = rs.getBoolean(column);
		return rs.wasNull() ? null : value;
	}

	private Instant toInstant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private Double roundRate(double rate) {
		return Math.round(rate * 10.0) / 10.0;
	}

	private record StatsAggregation(
		int spentAmount,
		int restrainedAmount,
		long boughtCount,
		long restrainedCount,
		Double rationalChoiceRate,
		long irrationalChoiceCount
	) {}

	PostListResponse getMyPosts(UUID userId, Instant cursor, int size) {
		PageRequest pageable = PageRequest.of(0, size + 1);
		var posts = cursor != null
			? feedPostRepository.findByUserIdVisibleBefore(userId, cursor, pageable)
			: feedPostRepository.findByUserIdVisible(userId, pageable);

		boolean hasMore = posts.size() > size;
		if (hasMore) posts = posts.subList(0, size);

		String myNickname = userProfileRepository.findByUserId(userId).isPresent()
			? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;
		List<UUID> blockedIds = blockService.getBlockedUserIds(userId);
		Map<UUID, Long> commentCounts = countVisibleCommentsByPostIds(
			posts.stream().map(post -> post.getId()).toList(),
			blockedIds);

		var items = posts.stream().map(post -> {
			long cc = commentCounts.getOrDefault(post.getId(), 0L);
			var myVote = postVoteRepository.findByPostIdAndUserId(post.getId(), userId)
				.filter(PostVote::isActive).map(PostVote::getVoteType).orElse(null);
			return PostResponse.of(post, myNickname, cc, myVote, userId);
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
		List<UUID> blockedIds = blockService.getBlockedUserIds(userId);
		Map<UUID, Long> commentCounts = countVisibleCommentsByPostIds(
			votes.stream().map(v -> v.getPost().getId()).toList(),
			blockedIds);

		var items = votes.stream().map(vote -> {
			var post = vote.getPost();
			String authorNickname = existingProfileIds.contains(post.getUser().getId())
				? UserProfile.POST_AUTHOR_NICKNAME : UserProfile.UNKNOWN_NICKNAME;
			long cc = commentCounts.getOrDefault(post.getId(), 0L);
			return PostResponse.of(post, authorNickname, cc, vote.getVoteType(), userId);
		}).toList();

		Instant nextCursor = hasMore && !votes.isEmpty() ? votes.get(votes.size() - 1).getCreatedAt() : null;
		return new PostListResponse(items, nextCursor, hasMore);
	}

	private UserAccount findUserOrThrow(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new MypageNotFoundException("사용자를 찾을 수 없습니다."));
	}

	private Map<UUID, Long> countVisibleCommentsByPostIds(List<UUID> postIds, List<UUID> blockedIds) {
		if (postIds.isEmpty()) return Collections.emptyMap();

		List<PostCommentCount> counts = blockedIds.isEmpty()
			? postCommentRepository.countVisibleByPostIds(postIds)
			: postCommentRepository.countVisibleByPostIdsExcludingBlockers(postIds, blockedIds);
		return counts.stream()
			.collect(Collectors.toMap(PostCommentCount::postId, PostCommentCount::commentCount));
	}
}

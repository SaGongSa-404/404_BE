package com.sagongsa.backend.home;

import com.sagongsa.backend.home.HomeSummaryResponse.BudgetSummary;
import com.sagongsa.backend.home.HomeSummaryResponse.BubbleSummary;
import com.sagongsa.backend.home.HomeSummaryResponse.MascotSummary;
import com.sagongsa.backend.home.HomeSummaryResponse.NotificationSummary;
import com.sagongsa.backend.home.HomeSummaryResponse.NotificationsSummary;
import com.sagongsa.backend.home.HomeSummaryResponse.UserProfileSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HomeSummaryService {

	private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");
	private static final int LATEST_NOTIFICATION_LIMIT = 3;
	private static final int NOTIFICATION_RETENTION_MONTHS = 1;
	static final List<String> BUDGET_NEGATIVE_BUBBLE_MESSAGES = List.of(
		"이번 달 예산을 넘었어요. 잠깐 멈춰볼까요?",
		"예산보다 더 썼어요. 오늘은 소비를 쉬어가도 좋아요.",
		"예산이 마이너스예요. 다음 위시는 조금만 더 고민해봐요."
	);
	static final List<String> BUDGET_ZERO_BUBBLE_MESSAGES = List.of(
		"이번 달 예산을 다 썼어요. 남은 기간은 신중하게 가봐요.",
		"예산이 0원이 되었어요. 오늘은 장바구니를 잠시 닫아볼까요?",
		"남은 예산이 없어요. 다음 소비는 한 번 더 생각해봐요."
	);
	static final List<String> PENDING_WISHLIST_BUBBLE_MESSAGES = List.of(
		"아직 결정하지 않은 위시템이 있어요.",
		"담아둔 위시템을 다시 한번 살펴볼까요?",
		"고민 중인 위시템을 하나 골라 결정해봐요."
	);
	static final List<String> VOTE_WAITING_BUBBLE_MESSAGES = List.of(
		"내 위시템에 투표가 들어왔어요.",
		"친구들의 투표 결과를 확인해볼까요?",
		"투표가 모였어요. 이제 결정할 차례예요."
	);
	static final List<String> DEFAULT_BUBBLE_MESSAGES = List.of(
		"오늘도 현명한 소비를 도와드릴게요.",
		"갖고 싶은 게 생기면 같이 천천히 고민해봐요.",
		"소비하기 전 한 번 더 생각하면 좋아요."
	);

	private final JdbcTemplate jdbcTemplate;

	public HomeSummaryService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public HomeSummaryResponse getSummary(UUID userId) {
		if (!userExists(userId)) {
			throw new HomeUserNotFoundException(userId);
		}
		if (!userCanUseHome(userId)) {
			throw new HomeForbiddenException("Home summary can be used only by active users who completed onboarding.");
		}

		UserProfileSummary userProfile = findUserProfile(userId)
			.orElseGet(() -> new UserProfileSummary(null, null, null));
		ZoneId zoneId = resolveZoneId(userProfile.timezone());
		String currentYearMonth = YearMonth.now(zoneId).toString();
		MascotSummary mascot = findMascotSummary(userId).orElseGet(HomeSummaryService::defaultMascotSummary);
		BudgetSummary budget = findBudgetSummary(userId, currentYearMonth).orElseGet(() -> defaultBudgetSummary(currentYearMonth));

		return new HomeSummaryResponse(
			userProfile,
			mascot,
			budget,
			homeBubble(userId, mascot, budget),
			new NotificationsSummary(countUnreadNotifications(userId), findLatestNotifications(userId)),
			findRationalChoiceRate(userId, currentYearMonth, zoneId)
		);
	}

	private boolean userExists(UUID userId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"select exists(select 1 from users where id = ?)",
			Boolean.class,
			userId
		);
		return Boolean.TRUE.equals(exists);
	}

	private boolean userCanUseHome(UUID userId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"""
			select exists(
				select 1
				from users
				where id = ?
				  and status = 'ACTIVE'
				  and onboarding_status = 'COMPLETED'
			)
			""",
			Boolean.class,
			userId
		);
		return Boolean.TRUE.equals(exists);
	}

	private Optional<UserProfileSummary> findUserProfile(UUID userId) {
		return jdbcTemplate.query(
				"""
				select nickname, mascot_name, timezone
				from user_profiles
				where user_id = ?
				""",
				(rs, rowNum) -> new UserProfileSummary(
					rs.getString("nickname"),
					rs.getString("mascot_name"),
					rs.getString("timezone")
				),
				userId
			)
			.stream()
			.findFirst();
	}

	private Optional<MascotSummary> findMascotSummary(UUID userId) {
		return jdbcTemplate.query(
				"""
				select mascot_state, last_reaction_message, last_state_changed_at, reaction_expires_at
				from mascot_profiles
				where user_id = ?
				""",
				(rs, rowNum) -> new MascotSummary(
					rs.getString("mascot_state"),
					rs.getString("last_reaction_message"),
					readInstant(rs, "last_state_changed_at"),
					readInstant(rs, "reaction_expires_at")
				),
				userId
			)
			.stream()
			.map(this::resetExpiredMascotReaction)
			.findFirst();
	}

	private Optional<BudgetSummary> findBudgetSummary(UUID userId, String yearMonth) {
		return jdbcTemplate.query(
				"""
				select year_month, monthly_budget_amount, spent_amount, warning_threshold_rate,
				       budget_exhaustion_bubble_seen
				from budget_cycles
				where user_id = ?
				  and year_month = ?
				""",
				(rs, rowNum) -> {
					int monthlyBudget = rs.getInt("monthly_budget_amount");
					int spent = rs.getInt("spent_amount");
					boolean bubbleSeen = rs.getBoolean("budget_exhaustion_bubble_seen");
					boolean exhausted = budgetExhausted(monthlyBudget, spent);
					return new BudgetSummary(
						rs.getString("year_month"),
						monthlyBudget,
						spent,
						remainingAmount(monthlyBudget, spent),
						rs.getBigDecimal("warning_threshold_rate"),
						exhausted,
						exhausted && !bubbleSeen
					);
				},
				userId,
				yearMonth
			)
			.stream()
			.findFirst();
	}

	@org.springframework.transaction.annotation.Transactional
	public void markBubbleSeen(UUID userId) {
		markBudgetExhaustionBubbleSeen(userId);
	}

	@org.springframework.transaction.annotation.Transactional
	public void markBubbleSeen(UUID userId, String type) {
		if (type == null || type.isBlank()) {
			return;
		}
		switch (type.toUpperCase(java.util.Locale.ROOT)) {
			case "WELCOME" -> markWelcomeBubbleSeen(userId);
			case "BUDGET_NEGATIVE", "BUDGET_ZERO" -> markBudgetExhaustionBubbleSeen(userId);
			case "DECISION_REACTION" -> clearDecisionReactionBubble(userId);
			default -> {
			}
		}
	}

	private void markBudgetExhaustionBubbleSeen(UUID userId) {
		String timezone = jdbcTemplate.query(
				"select timezone from user_profiles where user_id = ?",
				(rs, rowNum) -> rs.getString("timezone"),
				userId
			)
			.stream().findFirst().orElse(null);
		String currentYearMonth = YearMonth.now(resolveZoneId(timezone)).toString();
		jdbcTemplate.update(
			"""
			update budget_cycles
			   set budget_exhaustion_bubble_seen = true,
			       updated_at = now()
			 where user_id = ?
			   and year_month = ?
			   and monthly_budget_amount > 0
			   and spent_amount >= monthly_budget_amount
			""",
			userId,
			currentYearMonth
		);
	}

	private void markWelcomeBubbleSeen(UUID userId) {
		jdbcTemplate.update(
			"""
			update mascot_profiles
			   set welcome_bubble_seen = true,
			       updated_at = now()
			 where user_id = ?
			""",
			userId
		);
	}

	private void clearDecisionReactionBubble(UUID userId) {
		jdbcTemplate.update(
			"""
			update mascot_profiles
			   set last_reaction_message = null,
			       reaction_expires_at = null,
			       updated_at = now()
			 where user_id = ?
			""",
			userId
		);
	}

	private BubbleSummary homeBubble(UUID userId, MascotSummary mascot, BudgetSummary budget) {
		if (budget.showBudgetExhaustionBubble()) {
			if (budget.spentAmount() > budget.monthlyBudgetAmount()) {
				return bubble("BUDGET_NEGATIVE", randomValue(BUDGET_NEGATIVE_BUBBLE_MESSAGES), 100);
			}
			return bubble("BUDGET_ZERO", randomValue(BUDGET_ZERO_BUBBLE_MESSAGES), 90);
		}
		if (mascot.lastReactionMessage() != null && !mascot.lastReactionMessage().isBlank()) {
			return bubble("DECISION_REACTION", mascot.lastReactionMessage(), 80);
		}
		if (welcomeBubblePending(userId)) {
			return bubble("WELCOME", "반가워요! 같이 현명한 소비해봐요", 70);
		}
		if (hasPendingWishlist(userId)) {
			return bubble("PENDING_WISHLIST", randomValue(PENDING_WISHLIST_BUBBLE_MESSAGES), 50);
		}
		if (hasVoteWaiting(userId)) {
			return bubble("VOTE_WAITING", randomValue(VOTE_WAITING_BUBBLE_MESSAGES), 40);
		}
		return bubble("DEFAULT", randomValue(DEFAULT_BUBBLE_MESSAGES), 10);
	}

	private BubbleSummary bubble(String type, String message, int priority) {
		return new BubbleSummary(type, message, priority, true, "/api/v1/home/bubbles/" + type + "/seen");
	}

	private String randomValue(List<String> values) {
		return values.get(ThreadLocalRandom.current().nextInt(values.size()));
	}

	private boolean welcomeBubblePending(UUID userId) {
		Boolean pending = jdbcTemplate.queryForObject(
			"""
			select exists(
				select 1
				from mascot_profiles
				where user_id = ?
				  and welcome_bubble_seen = false
			)
			""",
			Boolean.class,
			userId
		);
		return Boolean.TRUE.equals(pending);
	}

	private boolean hasPendingWishlist(UUID userId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"""
			select exists(
				select 1
				from saved_items
				where user_id = ?
				  and status = 'SAVED'
			)
			""",
			Boolean.class,
			userId
		);
		return Boolean.TRUE.equals(exists);
	}

	private boolean hasVoteWaiting(UUID userId) {
		Boolean exists = jdbcTemplate.queryForObject(
			"""
			select exists(
				select 1
				from feed_posts fp
				join post_votes pv on pv.post_id = fp.id
				where fp.user_id = ?
				  and fp.deleted_at is null
				  and fp.moderation_status = 'ACTIVE'
				  and pv.canceled_at is null
			)
			""",
			Boolean.class,
			userId
		);
		return Boolean.TRUE.equals(exists);
	}

	private long countUnreadNotifications(UUID userId) {
		Long count = jdbcTemplate.queryForObject(
			"""
			select count(*)
			from notifications
			where user_id = ?
			  and is_read = false
			  and created_at >= ?
			""",
			Long.class,
			userId,
			notificationRetentionCutoff()
		);
		return count == null ? 0 : count;
	}

	private List<NotificationSummary> findLatestNotifications(UUID userId) {
		return jdbcTemplate.query(
			"""
			select id, notification_type, title, body, target_path, is_read, created_at
			from notifications
			where user_id = ?
			  and created_at >= ?
			order by created_at desc, id desc
			limit ?
			""",
			(rs, rowNum) -> new NotificationSummary(
				rs.getObject("id", UUID.class),
				rs.getString("notification_type"),
				rs.getString("title"),
				rs.getString("body"),
				rs.getString("target_path"),
				rs.getBoolean("is_read"),
				readInstant(rs, "created_at")
			),
			userId,
			notificationRetentionCutoff(),
			LATEST_NOTIFICATION_LIMIT
		);
	}

	private OffsetDateTime notificationRetentionCutoff() {
		return OffsetDateTime.now(java.time.ZoneOffset.UTC).minusMonths(NOTIFICATION_RETENTION_MONTHS);
	}

	private BigDecimal findRationalChoiceRate(UUID userId, String yearMonth, ZoneId zoneId) {
		YearMonth targetMonth = YearMonth.parse(yearMonth);
		OffsetDateTime monthStart = OffsetDateTime.from(targetMonth.atDay(1).atStartOfDay(zoneId));
		OffsetDateTime nextMonthStart = OffsetDateTime.from(targetMonth.plusMonths(1).atDay(1).atStartOfDay(zoneId));
		List<RationalChoiceAggregate> aggregates = jdbcTemplate.query(
				"""
				select
				  count(*) as total_count,
				  coalesce(sum(
				    case
				      when result = 'GO' and rationality_result = 'IRRATIONAL' then 0
				      else 1
				    end
				  ), 0) as rational_count
				from purchase_decisions
				where user_id = ?
				  and decided_at >= ?
				  and decided_at < ?
				""",
				(rs, rowNum) -> new RationalChoiceAggregate(
					rs.getLong("total_count"),
					rs.getLong("rational_count")
				),
				userId,
				monthStart,
				nextMonthStart
			);
		RationalChoiceAggregate aggregate = aggregates.getFirst();
		if (aggregate.totalCount() == 0) {
			return null;
		}
		return BigDecimal.valueOf(aggregate.rationalCount())
			.multiply(BigDecimal.valueOf(100))
			.divide(BigDecimal.valueOf(aggregate.totalCount()), 2, RoundingMode.HALF_UP);
	}

	private static MascotSummary defaultMascotSummary() {
		return new MascotSummary("DEFAULT", null, null, null);
	}

	private MascotSummary resetExpiredMascotReaction(MascotSummary mascotSummary) {
		if (mascotSummary.reactionExpiresAt() != null && !mascotSummary.reactionExpiresAt().isAfter(Instant.now())) {
			return defaultMascotSummary();
		}
		return mascotSummary;
	}

	private static BudgetSummary defaultBudgetSummary(String yearMonth) {
		return new BudgetSummary(yearMonth, 0, 0, 0, BigDecimal.ZERO, false, false);
	}

	private static int remainingAmount(int monthlyBudgetAmount, int spentAmount) {
		return Math.max(0, monthlyBudgetAmount - spentAmount);
	}

	private static boolean budgetExhausted(int monthlyBudgetAmount, int spentAmount) {
		return monthlyBudgetAmount > 0 && spentAmount >= monthlyBudgetAmount;
	}

	private static ZoneId resolveZoneId(String timezone) {
		if (timezone == null || timezone.isBlank()) {
			return DEFAULT_ZONE_ID;
		}
		try {
			return ZoneId.of(timezone);
		} catch (DateTimeException ignored) {
			return DEFAULT_ZONE_ID;
		}
	}

	private static Instant readInstant(ResultSet resultSet, String columnName) throws SQLException {
		Timestamp timestamp = resultSet.getTimestamp(columnName);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private record RationalChoiceAggregate(long totalCount, long rationalCount) {
	}
}

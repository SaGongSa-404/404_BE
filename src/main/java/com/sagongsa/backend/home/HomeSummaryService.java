package com.sagongsa.backend.home;

import com.sagongsa.backend.home.HomeSummaryResponse.BudgetSummary;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HomeSummaryService {

	private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");
	private static final int LATEST_NOTIFICATION_LIMIT = 3;

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

		return new HomeSummaryResponse(
			userProfile,
			findMascotSummary(userId).orElseGet(HomeSummaryService::defaultMascotSummary),
			findBudgetSummary(userId, currentYearMonth).orElseGet(() -> defaultBudgetSummary(currentYearMonth)),
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
					boolean exhausted = monthlyBudget > 0 && spent >= monthlyBudget;
					return new BudgetSummary(
						rs.getString("year_month"),
						monthlyBudget,
						spent,
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

	private long countUnreadNotifications(UUID userId) {
		Long count = jdbcTemplate.queryForObject(
			"""
			select count(*)
			from notifications
			where user_id = ?
			  and is_read = false
			""",
			Long.class,
			userId
		);
		return count == null ? 0 : count;
	}

	private List<NotificationSummary> findLatestNotifications(UUID userId) {
		return jdbcTemplate.query(
			"""
			select id, notification_type, title, body, target_path, is_read, created_at
			from notifications
			where user_id = ?
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
			LATEST_NOTIFICATION_LIMIT
		);
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
		return new BudgetSummary(yearMonth, 0, 0, BigDecimal.ZERO, false, false);
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

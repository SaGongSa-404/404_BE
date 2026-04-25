package com.sagongsa.backend.home;

import com.sagongsa.backend.home.HomeSummaryResponse.BudgetSummary;
import com.sagongsa.backend.home.HomeSummaryResponse.MascotSummary;
import com.sagongsa.backend.home.HomeSummaryResponse.NotificationSummary;
import com.sagongsa.backend.home.HomeSummaryResponse.NotificationsSummary;
import com.sagongsa.backend.home.HomeSummaryResponse.UserProfileSummary;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.Instant;
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

		UserProfileSummary userProfile = findUserProfile(userId)
			.orElseGet(() -> new UserProfileSummary(null, null, null));
		String currentYearMonth = YearMonth.now(resolveZoneId(userProfile.timezone())).toString();

		return new HomeSummaryResponse(
			userProfile,
			findMascotSummary(userId).orElseGet(HomeSummaryService::defaultMascotSummary),
			findBudgetSummary(userId, currentYearMonth).orElseGet(() -> defaultBudgetSummary(currentYearMonth)),
			new NotificationsSummary(countUnreadNotifications(userId), findLatestNotifications(userId)),
			null
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
			.findFirst();
	}

	private Optional<BudgetSummary> findBudgetSummary(UUID userId, String yearMonth) {
		return jdbcTemplate.query(
				"""
				select year_month, monthly_budget_amount, spent_amount, warning_threshold_rate
				from budget_cycles
				where user_id = ?
				  and year_month = ?
				""",
				(rs, rowNum) -> new BudgetSummary(
					rs.getString("year_month"),
					rs.getInt("monthly_budget_amount"),
					rs.getInt("spent_amount"),
					rs.getBigDecimal("warning_threshold_rate")
				),
				userId,
				yearMonth
			)
			.stream()
			.findFirst();
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

	private static MascotSummary defaultMascotSummary() {
		return new MascotSummary("DEFAULT", null, null, null);
	}

	private static BudgetSummary defaultBudgetSummary(String yearMonth) {
		return new BudgetSummary(yearMonth, 0, 0, BigDecimal.ZERO);
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
}

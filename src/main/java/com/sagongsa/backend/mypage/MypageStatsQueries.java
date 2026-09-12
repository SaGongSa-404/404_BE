package com.sagongsa.backend.mypage;

import com.sagongsa.backend.domain.auth.UserAccount;
import com.sagongsa.backend.domain.auth.UserAccountRepository;
import com.sagongsa.backend.domain.budget.BudgetCycle;
import com.sagongsa.backend.domain.budget.BudgetCycleRolloverService;
import com.sagongsa.backend.domain.budget.BudgetCycleRepository;
import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.domain.enums.ItemStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
@Transactional(readOnly = true)
class MypageStatsQueries {
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private final UserAccountRepository userAccountRepository;
	private final BudgetCycleRepository budgetCycleRepository;
	private final BudgetCycleRolloverService budgetCycleRolloverService;
	private final JdbcTemplate jdbcTemplate;

	MypageStatsQueries(UserAccountRepository userAccountRepository, BudgetCycleRepository budgetCycleRepository, BudgetCycleRolloverService budgetCycleRolloverService, JdbcTemplate jdbcTemplate) {
		this.userAccountRepository = userAccountRepository;
		this.budgetCycleRepository = budgetCycleRepository;
		this.budgetCycleRolloverService = budgetCycleRolloverService;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	AvailableMonthsResponse getAvailableMonths(UUID userId) {
		findUserOrThrow(userId);
		String current = YearMonth.now(KST).toString();
		budgetCycleRolloverService.ensureBudgetCycle(userId, current);
		Set<String> months = new LinkedHashSet<>(budgetCycleRepository.findYearMonthsByUserId(userId));
		months.add(current);
		List<String> sorted = new ArrayList<>(months);
		sorted.sort((a, b) -> b.compareTo(a));
		return new AvailableMonthsResponse(sorted, current);
	}

	@Transactional
	StatsResponse getStats(UUID userId, String yearMonth) {
		if (yearMonth == null || yearMonth.isBlank()) {
			yearMonth = YearMonth.now(KST).toString();
		}
		findUserOrThrow(userId);
		YearMonth ym = YearMonth.parse(yearMonth);
		if (YearMonth.now(KST).equals(ym)) {
			budgetCycleRolloverService.ensureBudgetCycle(userId, ym.toString());
		}
		Instant from = ym.atDay(1).atStartOfDay(KST).toInstant();
		Instant to = ym.atEndOfMonth().plusDays(1).atStartOfDay(KST).toInstant();

		BudgetCycle budget = budgetCycleRepository.findByUserIdAndYearMonth(userId, yearMonth).orElse(null);
		Integer budgetAmount = budget != null ? budget.getMonthlyBudgetAmount() : null;
		StatsAggregation stats = getStatsAggregation(userId, from, to);
		List<CategorySpendAmountResponse> categorySpendAmounts = getCategorySpendAmounts(userId, from, to);
        // Unrated actual purchases contribute money/counts, never fabricated rationality scores.
        var actual = jdbcTemplate.queryForMap("""
            select coalesce(sum(po.actual_price),0)::bigint as amount, count(*) as count
            from purchase_outcomes po join saved_items si on si.id=po.item_id
            join budget_cycles bc on bc.id=po.budget_cycle_id
            where si.user_id=? and po.status='PURCHASED' and bc.year_month=?
            """, userId, yearMonth);
        stats = new StatsAggregation(stats.spentAmount()+((Number)actual.get("amount")).longValue(),
            stats.restrainedAmount(), stats.boughtCount()+((Number)actual.get("count")).longValue(),
            stats.restrainedCount(), stats.rationalChoiceRate(), stats.irrationalChoiceCount());
        var categoryTotals = new java.util.EnumMap<ItemCategory,Long>(ItemCategory.class);
        categorySpendAmounts.forEach(row -> categoryTotals.merge(row.category(),row.amount(),Long::sum));
        jdbcTemplate.query("""
            select si.category, sum(po.actual_price)::bigint as amount from purchase_outcomes po
            join saved_items si on si.id=po.item_id join budget_cycles bc on bc.id=po.budget_cycle_id
            where si.user_id=? and po.status='PURCHASED' and bc.year_month=? group by si.category
            """, (rs,n) -> new CategorySpendAmountResponse(ItemCategory.valueOf(rs.getString("category")),rs.getLong("amount")), userId,yearMonth)
            .forEach(row -> categoryTotals.merge(row.category(),row.amount(),Long::sum));
        categorySpendAmounts = categoryTotals.entrySet().stream()
            .map(entry -> new CategorySpendAmountResponse(entry.getKey(),entry.getValue()))
            .sorted(java.util.Comparator.comparingLong(CategorySpendAmountResponse::amount).reversed()
                .thenComparing(row -> row.category().name())).toList();

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
		String statusName = status == null ? null : status.name();
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
			  AND (CAST(? AS varchar) IS NULL OR si.status = ?)
			  AND pd.decided_at >= ?
			  AND pd.decided_at < ?
			ORDER BY pd.decided_at DESC
			LIMIT ? OFFSET ?
			""",
			this::mapWishSummary,
			userId,
			statusName,
			statusName,
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
			           THEN COALESCE(pd.final_price, si.listed_price, 0) ELSE 0 END), 0)::bigint AS spent_amount,
			       COALESCE(SUM(CASE WHEN pd.result = 'STOP'
			           THEN COALESCE(si.listed_price, 0) ELSE 0 END), 0)::bigint AS restrained_amount,
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
					rs.getLong("spent_amount"),
					rs.getLong("restrained_amount"),
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
			       COALESCE(SUM(COALESCE(pd.final_price, si.listed_price, 0)), 0)::bigint AS amount
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
				rs.getLong("amount")
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

	private UserAccount findUserOrThrow(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new MypageNotFoundException("사용자를 찾을 수 없습니다."));
	}
	private record StatsAggregation(
		long spentAmount,
		long restrainedAmount,
		long boughtCount,
		long restrainedCount,
		Double rationalChoiceRate,
		long irrationalChoiceCount
	) {}
}

package com.sagongsa.backend.deliberation;

import com.sagongsa.backend.deliberation.DeliberationSummaryResponse.BudgetProjection;
import com.sagongsa.backend.deliberation.DeliberationSummaryResponse.ItemSummary;
import com.sagongsa.backend.deliberation.DeliberationSummaryResponse.SelfCheckQuestion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeliberationService {

	private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");

	static final List<SelfCheckQuestion> SELF_CHECK_QUESTIONS = List.of(
		new SelfCheckQuestion("Q1", "오늘 갑자기 갖고 싶어진 건가요?"),
		new SelfCheckQuestion("Q2", "비슷한 물건을 이미 갖고 있나요?"),
		new SelfCheckQuestion("Q3", "구매하지 않아도 일상생활에 불편함이 없나요?"),
		new SelfCheckQuestion("Q4", "한 달 뒤에는 필요하지 않을 것 같나요?")
	);

	private final JdbcTemplate jdbcTemplate;

	public DeliberationService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public DeliberationSummaryResponse getSummary(UUID userId, UUID itemId) {
		UserContext user = requireUsableUser(userId);
		ZoneId zoneId = resolveZoneId(user.timezone());
		String yearMonth = YearMonth.now(zoneId).toString();
		ItemSummary item = findSavedItem(userId, itemId);
		BudgetSnapshot budget = findBudget(userId, yearMonth);
		int itemPrice = item.listedPrice() == null ? 0 : item.listedPrice();
		int projectedSpentAmount = budget.spentAmount() + itemPrice;
		BigDecimal projectedUsageRate = usageRate(projectedSpentAmount, budget.monthlyBudgetAmount());

		return new DeliberationSummaryResponse(
			item,
			new BudgetProjection(
				yearMonth,
				budget.monthlyBudgetAmount(),
				budget.spentAmount(),
				projectedSpentAmount,
				projectedUsageRate
			),
			similarCategorySpendAmount(userId, item.category(), yearMonth, zoneId),
			opportunityCostMessage(item),
			SELF_CHECK_QUESTIONS
		);
	}

	private UserContext requireUsableUser(UUID userId) {
		try {
			UserContext user = jdbcTemplate.queryForObject(
				"""
				select u.status, u.onboarding_status, coalesce(up.timezone, 'Asia/Seoul') as timezone
				from users u
				left join user_profiles up on up.user_id = u.id
				where u.id = ?
				""",
				(rs, rowNumber) -> new UserContext(
					rs.getString("status"),
					rs.getString("onboarding_status"),
					rs.getString("timezone")
				),
				userId
			);
			if (!Objects.equals(user.status(), "ACTIVE") || !Objects.equals(user.onboardingStatus(), "COMPLETED")) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Deliberation can be used only after onboarding.");
			}
			return user;
		}
		catch (EmptyResultDataAccessException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found.");
		}
	}

	private ItemSummary findSavedItem(UUID userId, UUID itemId) {
		try {
			ItemSummary item = jdbcTemplate.queryForObject(
				"""
				select id, title, image_url, listed_price, currency_code, category, status
				from saved_items
				where user_id = ?
				  and id = ?
				""",
				this::mapItem,
				userId,
				itemId
			);
			if (!Objects.equals(item.status(), "SAVED")) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Only saved wishlist items can enter deliberation.");
			}
			return item;
		}
		catch (EmptyResultDataAccessException exception) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved wishlist item was not found.");
		}
	}

	private BudgetSnapshot findBudget(UUID userId, String yearMonth) {
		return jdbcTemplate.query(
				"""
				select monthly_budget_amount, spent_amount
				from budget_cycles
				where user_id = ?
				  and year_month = ?
				""",
				(rs, rowNumber) -> new BudgetSnapshot(rs.getInt("monthly_budget_amount"), rs.getInt("spent_amount")),
				userId,
				yearMonth
			)
			.stream()
			.findFirst()
			.orElse(new BudgetSnapshot(0, 0));
	}

	private int similarCategorySpendAmount(UUID userId, String category, String yearMonth, ZoneId zoneId) {
		Integer amount = jdbcTemplate.queryForObject(
			"""
			select coalesce(sum(coalesce(pd.final_price, 0)), 0)::integer
			from purchase_decisions pd
			join saved_items si on si.id = pd.item_id
			where pd.user_id = ?
			  and pd.result = 'GO'
			  and si.category = ?
			  and to_char(pd.decided_at at time zone ?, 'YYYY-MM') = ?
			""",
			Integer.class,
			userId,
			category,
			zoneId.getId(),
			yearMonth
		);
		return amount == null ? 0 : amount;
	}

	private String opportunityCostMessage(ItemSummary item) {
		if (item.listedPrice() == null || item.listedPrice() == 0) {
			return "가격을 입력하면 다른 선택지와 비교하기 쉬워요.";
		}
		return "%,d원을 다른 곳에 쓸 수도 있어요.".formatted(item.listedPrice());
	}

	private BigDecimal usageRate(int spentAmount, int monthlyBudgetAmount) {
		if (monthlyBudgetAmount <= 0) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}
		return BigDecimal.valueOf(spentAmount)
			.multiply(BigDecimal.valueOf(100))
			.divide(BigDecimal.valueOf(monthlyBudgetAmount), 2, RoundingMode.HALF_UP);
	}

	private ItemSummary mapItem(ResultSet rs, int rowNumber) throws SQLException {
		int listedPrice = rs.getInt("listed_price");
		Integer nullableListedPrice = rs.wasNull() ? null : listedPrice;
		return new ItemSummary(
			rs.getObject("id", UUID.class),
			rs.getString("title"),
			rs.getString("image_url"),
			nullableListedPrice,
			rs.getString("currency_code"),
			rs.getString("category"),
			rs.getString("status")
		);
	}

	private ZoneId resolveZoneId(String timezone) {
		try {
			return ZoneId.of(timezone);
		}
		catch (DateTimeException exception) {
			return DEFAULT_ZONE_ID;
		}
	}

	private record UserContext(String status, String onboardingStatus, String timezone) {
	}

	private record BudgetSnapshot(int monthlyBudgetAmount, int spentAmount) {
	}
}

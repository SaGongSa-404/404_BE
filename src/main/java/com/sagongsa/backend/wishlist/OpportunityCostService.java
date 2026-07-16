package com.sagongsa.backend.wishlist;

import com.sagongsa.backend.domain.enums.ItemCategory;
import com.sagongsa.backend.wishlist.OpportunityCostResponse.OpportunityCostResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OpportunityCostService {

	private static final int MIN_PRICE_FOR_NORMAL_SELECTION = 5000;
	private static final int PRIMARY_MAX_COUNT = 15;
	private static final int EXPANDED_MAX_COUNT = 20;
	private static final String LOW_PRICE_FALLBACK_ITEM_ID = "ITEM_01";
	private static final String HIGH_PRICE_FALLBACK_ITEM_ID = "ITEM_15";
	private static final String LOW_PRICE_FALLBACK_MESSAGE = "이 돈을 아끼면, 시원한 아아 1잔을 마실 수 있어요! ☕";
	private static final String HIGH_PRICE_FALLBACK_MESSAGE = "이 돈을 아끼면, 한 학기 대학 등록금을 내고도 남아요! 🎓";

	private final JdbcTemplate jdbcTemplate;

	public OpportunityCostService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public OpportunityCostResponse calculate(Integer price, String rawCategory) {
		int normalizedPrice = validatePrice(price);
		ItemCategory sourceCategory = parseCategory(rawCategory);
		List<OpportunityCostItem> items = loadEnabledItems();
		if (items.isEmpty()) {
			throw new BadRequestException("Opportunity cost items are not configured.");
		}

		if (normalizedPrice < MIN_PRICE_FOR_NORMAL_SELECTION) {
			return fallback(normalizedPrice, sourceCategory, items, LOW_PRICE_FALLBACK_ITEM_ID, LOW_PRICE_FALLBACK_MESSAGE);
		}

		OpportunityCostItem highestItem = highestUnitPriceItem(items);
		if (normalizedPrice > highestItem.unitPrice()) {
			return fallback(normalizedPrice, sourceCategory, items, HIGH_PRICE_FALLBACK_ITEM_ID, HIGH_PRICE_FALLBACK_MESSAGE);
		}

		EnumSet<OpportunityCostCategory> targetPool = targetPool(categoryGroup(sourceCategory));
		List<CalculatedOpportunityCostItem> candidates = candidates(items, targetPool, normalizedPrice, PRIMARY_MAX_COUNT);
		if (candidates.isEmpty()) {
			candidates = candidates(items, targetPool, normalizedPrice, EXPANDED_MAX_COUNT);
		}
		if (candidates.isEmpty()) {
			return fallback(normalizedPrice, sourceCategory, items, HIGH_PRICE_FALLBACK_ITEM_ID, HIGH_PRICE_FALLBACK_MESSAGE);
		}

		CalculatedOpportunityCostItem selected = selectCandidate(candidates, normalizedPrice);
		return response(normalizedPrice, sourceCategory, selected.item(), selected.calculatedCount(), null);
	}

	private CalculatedOpportunityCostItem selectCandidate(
		List<CalculatedOpportunityCostItem> candidates,
		int price
	) {
		return candidates.stream()
			.filter(candidate -> candidate.calculatedCount() == 1)
			.min(Comparator.comparingLong(candidate -> Math.abs((long) candidate.item().unitPrice() - price)))
			.orElseGet(() -> randomValue(candidates));
	}

	private int validatePrice(Integer price) {
		if (price == null) {
			throw new BadRequestException("price is required.");
		}
		if (price <= 0) {
			throw new BadRequestException("price must be positive.");
		}
		return price;
	}

	private ItemCategory parseCategory(String rawCategory) {
		if (!StringUtils.hasText(rawCategory)) {
			throw new BadRequestException("category is required.");
		}
		try {
			return ItemCategory.valueOf(rawCategory.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new BadRequestException("category must be a supported item category.");
		}
	}

	private List<OpportunityCostItem> loadEnabledItems() {
		return jdbcTemplate.query(
			"""
			select item_id, target_category, unit_price, display_title, message_template
			from opportunity_cost_items
			where enabled = true
			order by sort_order
			""",
			this::mapItem
		);
	}

	private OpportunityCostItem mapItem(ResultSet rs, int rowNumber) throws SQLException {
		return new OpportunityCostItem(
			rs.getString("item_id"),
			OpportunityCostCategory.valueOf(rs.getString("target_category")),
			rs.getInt("unit_price"),
			rs.getString("display_title"),
			rs.getString("message_template")
		);
	}

	private OpportunityCostCategory categoryGroup(ItemCategory category) {
		return switch (category) {
			case FASHION -> OpportunityCostCategory.FASHION;
			case BEAUTY -> OpportunityCostCategory.BEAUTY;
			case DIGITAL -> OpportunityCostCategory.DIGITAL;
			case LIVING, FOOD, HOBBY, SUBSCRIPTION -> OpportunityCostCategory.LIFE;
			case ETC -> OpportunityCostCategory.OTHER;
		};
	}

	private EnumSet<OpportunityCostCategory> targetPool(OpportunityCostCategory sourceCategory) {
		return switch (sourceCategory) {
			case FASHION, BEAUTY -> EnumSet.of(
				OpportunityCostCategory.LIFE,
				OpportunityCostCategory.DIGITAL,
				OpportunityCostCategory.OTHER
			);
			case LIFE -> EnumSet.of(
				OpportunityCostCategory.FASHION,
				OpportunityCostCategory.BEAUTY,
				OpportunityCostCategory.DIGITAL
			);
			case DIGITAL -> EnumSet.of(
				OpportunityCostCategory.FASHION,
				OpportunityCostCategory.BEAUTY,
				OpportunityCostCategory.LIFE
			);
			case OTHER -> EnumSet.allOf(OpportunityCostCategory.class);
		};
	}

	private List<CalculatedOpportunityCostItem> candidates(
		List<OpportunityCostItem> items,
		EnumSet<OpportunityCostCategory> targetPool,
		int price,
		int maxCount
	) {
		return items.stream()
			.filter(item -> targetPool.contains(item.targetCategory()))
			.map(item -> new CalculatedOpportunityCostItem(item, calculatedCount(item.unitPrice(), price)))
			.filter(item -> item.calculatedCount() >= 1 && item.calculatedCount() <= maxCount)
			.toList();
	}

	private int calculatedCount(int unitPrice, int price) {
		return BigDecimal.valueOf(unitPrice)
			.divide(BigDecimal.valueOf(price), 0, RoundingMode.HALF_UP)
			.intValueExact();
	}

	private OpportunityCostItem highestUnitPriceItem(List<OpportunityCostItem> items) {
		return items.stream()
			.max((left, right) -> Integer.compare(left.unitPrice(), right.unitPrice()))
			.orElseThrow(() -> new BadRequestException("Opportunity cost items are not configured."));
	}

	private OpportunityCostResponse fallback(
		int price,
		ItemCategory sourceCategory,
		List<OpportunityCostItem> items,
		String itemId,
		String fallbackMessage
	) {
		OpportunityCostItem item = items.stream()
			.filter(candidate -> candidate.itemId().equals(itemId))
			.findFirst()
			.orElseThrow(() -> new BadRequestException("Opportunity cost fallback item is not configured."));
		return response(price, sourceCategory, item, 1, fallbackMessage);
	}

	private OpportunityCostResponse response(
		int price,
		ItemCategory sourceCategory,
		OpportunityCostItem item,
		int calculatedCount,
		String fallbackMessage
	) {
		String message = fallbackMessage == null
			? item.messageTemplate().replace("{N}", Integer.toString(calculatedCount))
			: fallbackMessage;
		return new OpportunityCostResponse(
			price,
			sourceCategory.name(),
			new OpportunityCostResult(
				item.itemId(),
				item.targetCategory().name(),
				calculatedCount,
				item.displayTitle(),
				message
			)
		);
	}

	private <T> T randomValue(List<T> values) {
		return values.get(ThreadLocalRandom.current().nextInt(values.size()));
	}

	private enum OpportunityCostCategory {
		FASHION,
		BEAUTY,
		LIFE,
		DIGITAL,
		OTHER
	}

	private record OpportunityCostItem(
		String itemId,
		OpportunityCostCategory targetCategory,
		int unitPrice,
		String displayTitle,
		String messageTemplate
	) {
	}

	private record CalculatedOpportunityCostItem(
		OpportunityCostItem item,
		int calculatedCount
	) {
	}
}

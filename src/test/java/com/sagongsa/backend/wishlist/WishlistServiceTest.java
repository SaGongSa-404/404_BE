package com.sagongsa.backend.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class WishlistServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private WishlistService wishlistService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	// ── TC1: URL 정규화 및 추적 파라미터 제거 ──────────────────────────────

	@Test
	void stripsUtmAndAdTrackingParametersOnCreate() {
		UUID userId = createActiveUser();

		WishlistItemResponse response = wishlistService.create(userId, shareRequest(
			"https://shop.example.com/product?id=100&utm_source=kakao&utm_medium=cpc&fbclid=abc123",
			"무선 이어폰"
		));

		assertThat(response.normalizedUrl()).isEqualTo("https://shop.example.com/product?id=100");
		assertThat(response.status()).isEqualTo("SAVED");
	}

	@Test
	void preservesNonTrackingQueryParametersOnCreate() {
		UUID userId = createActiveUser();

		WishlistItemResponse response = wishlistService.create(userId, shareRequest(
			"https://shop.example.com/search?q=sneakers&sort=price&utm_source=ig",
			"스니커즈"
		));

		assertThat(response.normalizedUrl()).isEqualTo("https://shop.example.com/search?q=sneakers&sort=price");
	}

	@Test
	void acceptsDirectInputWithUrl() {
		UUID userId = createActiveUser();

		WishlistItemResponse response = wishlistService.create(userId, new WishlistItemCreateRequest(
			"DIRECT_INPUT", "https://shop.example.com/item", null, "직접 입력 상품", null,
			29000, "KRW", "FASHION", null, false,
			null, null, null, null, null
		));

		assertThat(response.normalizedUrl()).isEqualTo("https://shop.example.com/item");
		assertThat(response.status()).isEqualTo("SAVED");
	}

	@Test
	void acceptsDirectInputWithoutUrl() {
		UUID userId = createActiveUser();

		WishlistItemResponse response = wishlistService.create(userId, new WishlistItemCreateRequest(
			"DIRECT_INPUT", null, null, "직접 입력 상품", null,
			29000, "KRW", "FASHION", null, false,
			null, null, null, null, null
		));

		assertThat(response.originalUrl()).isNull();
		assertThat(response.normalizedUrl()).isNull();
		assertThat(response.status()).isEqualTo("SAVED");
	}

	@Test
	void rejectsNonHttpScheme() {
		UUID userId = createActiveUser();

		assertThatThrownBy(() -> wishlistService.create(userId, shareRequest("ftp://files.example.com/item", "상품")))
			.isInstanceOf(BadRequestException.class);
	}

	// ── TC2: 생성 입력값 검증 ───────────────────────────────────────────────

	@Test
	void rejectsBlankTitle() {
		UUID userId = createActiveUser();

		assertThatThrownBy(() -> wishlistService.create(userId, new WishlistItemCreateRequest(
			"SHARE", "https://shop.example.com/product", null, "   ", null,
			null, null, "DIGITAL", null, false,
			null, null, null, null, null
		)))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void rejectsNegativeListedPrice() {
		UUID userId = createActiveUser();

		assertThatThrownBy(() -> wishlistService.create(userId, new WishlistItemCreateRequest(
			"SHARE", "https://shop.example.com/product", null, "상품", null,
			-1, null, "DIGITAL", null, false,
			null, null, null, null, null
		)))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void rejectsCurrencyCodeNotThreeCharacters() {
		UUID userId = createActiveUser();

		assertThatThrownBy(() -> wishlistService.create(userId, new WishlistItemCreateRequest(
			"SHARE", "https://shop.example.com/product", null, "상품", null,
			10000, "KR", "DIGITAL", null, false,
			null, null, null, null, null
		)))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void rejectsCategoryConfidenceAbove100() {
		UUID userId = createActiveUser();

		assertThatThrownBy(() -> wishlistService.create(userId, new WishlistItemCreateRequest(
			"SHARE", "https://shop.example.com/product", null, "상품", null,
			null, null, "DIGITAL", BigDecimal.valueOf(100.01), false,
			null, null, null, null, null
		)))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void rejectsUnknownCategory() {
		UUID userId = createActiveUser();

		assertThatThrownBy(() -> wishlistService.create(userId, new WishlistItemCreateRequest(
			"SHARE", "https://shop.example.com/product", null, "상품", null,
			null, null, "INVALID_CATEGORY", null, false,
			null, null, null, null, null
		)))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void throwsDuplicateWhenSameNormalizedUrlAlreadySaved() {
		UUID userId = createActiveUser();
		wishlistService.create(userId, shareRequest("https://shop.example.com/product?id=1", "첫 번째"));

		assertThatThrownBy(() -> wishlistService.create(userId,
			shareRequest("https://shop.example.com/product?id=1&utm_source=kakao", "두 번째")))
			.isInstanceOf(DuplicateSavedItemException.class);
	}

	// ── TC3: 접근 제어 ──────────────────────────────────────────────────────

	@Test
	void throwsNotFoundWhenUserDoesNotExist() {
		assertThatThrownBy(() -> wishlistService.create(UUID.randomUUID(),
			shareRequest("https://shop.example.com/product", "상품")))
			.isInstanceOf(WishlistItemNotFoundException.class);
	}

	@Test
	void throwsForbiddenWhenOnboardingNotCompleted() {
		UUID userId = createUserWithOnboardingStatus("NOT_STARTED");

		assertThatThrownBy(() -> wishlistService.create(userId,
			shareRequest("https://shop.example.com/product", "상품")))
			.isInstanceOf(WishlistForbiddenException.class);
	}

	// ── TC4: 목록 조회 및 카테고리 필터 ────────────────────────────────────

	@Test
	void returnsEmptyListWhenNoItemsSaved() {
		UUID userId = createActiveUser();

		WishlistItemPageResponse response = wishlistService.list(userId, null, null, null);

		assertThat(response.items()).isEmpty();
	}

	@Test
	void returnsAllSavedItemsInDescendingOrder() {
		UUID userId = createActiveUser();
		wishlistService.create(userId, shareRequest("https://shop.example.com/a", "상품 A"));
		wishlistService.create(userId, shareRequest("https://shop.example.com/b", "상품 B"));

		WishlistItemPageResponse response = wishlistService.list(userId, null, null, null);

		assertThat(response.items()).hasSize(2);
		assertThat(response.items().get(0).title()).isEqualTo("상품 B");
		assertThat(response.items().get(1).title()).isEqualTo("상품 A");
	}

	@Test
	void filtersByCategory() {
		UUID userId = createActiveUser();
		wishlistService.create(userId, shareRequest("https://shop.example.com/a", "디지털 상품"));
		wishlistService.create(userId, new WishlistItemCreateRequest(
			"SHARE", "https://shop.example.com/b", null, "패션 상품", null,
			null, null, "FASHION", null, false,
			null, null, null, null, null
		));

		WishlistItemPageResponse response = wishlistService.list(userId, "DIGITAL", null, null);

		assertThat(response.items()).hasSize(1);
		assertThat(response.items().getFirst().category()).isEqualTo("DIGITAL");
	}

	@Test
	void rejectsInvalidCategoryOnList() {
		UUID userId = createActiveUser();

		assertThatThrownBy(() -> wishlistService.list(userId, "INVALID_CATEGORY", null, null))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void doesNotReturnDroppedItemsInList() {
		UUID userId = createActiveUser();
		WishlistItemResponse created = wishlistService.create(userId,
			shareRequest("https://shop.example.com/a", "상품"));
		wishlistService.drop(userId, created.id());

		WishlistItemPageResponse response = wishlistService.list(userId, null, null, null);

		assertThat(response.items()).isEmpty();
	}

	// ── TC5: 단건 조회 / 삭제 ─────────────────────────────────────────────

	@Test
	void throwsNotFoundWhenGettingNonExistentItem() {
		UUID userId = createActiveUser();

		assertThatThrownBy(() -> wishlistService.get(userId, UUID.randomUUID()))
			.isInstanceOf(WishlistItemNotFoundException.class);
	}

	@Test
	void throwsNotFoundWhenGettingDroppedItem() {
		UUID userId = createActiveUser();
		WishlistItemResponse created = wishlistService.create(userId,
			shareRequest("https://shop.example.com/product", "상품"));

		wishlistService.drop(userId, created.id());

		assertThatThrownBy(() -> wishlistService.get(userId, created.id()))
			.isInstanceOf(WishlistItemNotFoundException.class);
	}

	@Test
	void throwsNotFoundWhenDroppingNonExistentItem() {
		UUID userId = createActiveUser();

		assertThatThrownBy(() -> wishlistService.drop(userId, UUID.randomUUID()))
			.isInstanceOf(WishlistItemNotFoundException.class);
	}

	// ── TC6: 카테고리 수정 ──────────────────────────────────────────────────

	@Test
	void updatesCategoryAndLocksIt() {
		UUID userId = createActiveUser();
		WishlistItemResponse created = wishlistService.create(userId,
			shareRequest("https://shop.example.com/product", "상품"));

		WishlistItemResponse updated = wishlistService.updateCategory(
			userId, created.id(), new WishlistCategoryUpdateRequest("FASHION")
		);

		assertThat(updated.category()).isEqualTo("FASHION");
		assertThat(updated.categoryLockedByUser()).isTrue();
	}

	@Test
	void rejectsInvalidCategoryOnUpdate() {
		UUID userId = createActiveUser();
		WishlistItemResponse created = wishlistService.create(userId,
			shareRequest("https://shop.example.com/product", "상품"));

		assertThatThrownBy(() -> wishlistService.updateCategory(
			userId, created.id(), new WishlistCategoryUpdateRequest("INVALID")
		))
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void throwsNotFoundWhenUpdatingNonExistentItem() {
		UUID userId = createActiveUser();

		assertThatThrownBy(() -> wishlistService.updateCategory(
			userId, UUID.randomUUID(), new WishlistCategoryUpdateRequest("FASHION")
		))
			.isInstanceOf(WishlistItemNotFoundException.class);
	}

	// ── 헬퍼 ───────────────────────────────────────────────────────────────

	private UUID createActiveUser() {
		return createUserWithOnboardingStatus("COMPLETED");
	}

	private UUID createUserWithOnboardingStatus(String onboardingStatus) {
		UUID userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
			"insert into users (id, status, onboarding_status, created_at, updated_at) values (?, 'ACTIVE', ?, ?, ?)",
			userId, onboardingStatus, now, now
		);
		return userId;
	}

	private WishlistItemCreateRequest shareRequest(String url, String title) {
		return new WishlistItemCreateRequest(
			"SHARE", url, null, title, null,
			null, null, "DIGITAL", null, false,
			null, null, null, null, null
		);
	}
}

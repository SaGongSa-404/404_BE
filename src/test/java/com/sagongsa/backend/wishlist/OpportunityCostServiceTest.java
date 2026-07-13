package com.sagongsa.backend.wishlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OpportunityCostServiceTest extends PostgreSqlContainerTest {

	@Autowired
	private OpportunityCostService opportunityCostService;

	@Test
	void calculatesSingleCandidateFromExistingLifeCategoryGroup() {
		OpportunityCostResponse response = opportunityCostService.calculate(2_000_000, "LIVING");

		assertThat(response.originalPrice()).isEqualTo(2_000_000);
		assertThat(response.sourceCategory()).isEqualTo("LIVING");
		assertThat(response.result().itemId()).isEqualTo("ITEM_14");
		assertThat(response.result().targetCategory()).isEqualTo("DIGITAL");
		assertThat(response.result().calculatedCount()).isEqualTo(1);
		assertThat(response.result().displayMessage())
			.isEqualTo("이 상품 1개를 아끼면, 200만 원 상당의 최신형 노트북을 살 수 있어요! 💻");
	}

	@Test
	void expandsCandidateCountToTwentyWhenPrimaryPoolIsEmpty() {
		OpportunityCostResponse response = opportunityCostService.calculate(5_000, "LIVING");

		assertThat(response.result().itemId()).isEqualTo("ITEM_05");
		assertThat(response.result().targetCategory()).isEqualTo("FASHION");
		assertThat(response.result().calculatedCount()).isEqualTo(20);
	}

	@Test
	void mapsFoodHobbyAndSubscriptionToLifeSourceGroup() {
		for (String category : new String[] {"FOOD", "HOBBY", "SUBSCRIPTION"}) {
			OpportunityCostResponse response = opportunityCostService.calculate(2_000_000, category);

			assertThat(response.sourceCategory()).isEqualTo(category);
			assertThat(response.result().itemId()).isEqualTo("ITEM_14");
			assertThat(response.result().targetCategory()).isEqualTo("DIGITAL");
		}
	}

	@Test
	void excludesAssociatedCategoryForFashionSource() {
		for (int index = 0; index < 20; index++) {
			OpportunityCostResponse response = opportunityCostService.calculate(120_000, "FASHION");

			assertThat(response.result().targetCategory()).isNotEqualTo("BEAUTY");
			assertThat(response.result().calculatedCount()).isBetween(1, 15);
		}
	}

	@Test
	void returnsLowPriceFallback() {
		OpportunityCostResponse response = opportunityCostService.calculate(2_500, "DIGITAL");

		assertThat(response.result().itemId()).isEqualTo("ITEM_01");
		assertThat(response.result().targetCategory()).isEqualTo("LIFE");
		assertThat(response.result().calculatedCount()).isEqualTo(1);
		assertThat(response.result().displayMessage()).isEqualTo("이 돈을 아끼면, 시원한 아아 1잔을 마실 수 있어요! ☕");
	}

	@Test
	void returnsHighPriceFallback() {
		OpportunityCostResponse response = opportunityCostService.calculate(5_000_000, "LIVING");

		assertThat(response.result().itemId()).isEqualTo("ITEM_15");
		assertThat(response.result().targetCategory()).isEqualTo("OTHER");
		assertThat(response.result().calculatedCount()).isEqualTo(1);
		assertThat(response.result().displayMessage()).isEqualTo("이 돈을 아끼면, 한 학기 대학 등록금을 내고도 남아요! 🎓");
	}

	@Test
	void rejectsInvalidInput() {
		assertThatThrownBy(() -> opportunityCostService.calculate(0, "DIGITAL"))
			.isInstanceOf(BadRequestException.class);
		assertThatThrownBy(() -> opportunityCostService.calculate(10_000, "INVALID"))
			.isInstanceOf(BadRequestException.class);
	}
}

package com.sagongsa.backend.wishlist;

import static org.hamcrest.Matchers.isIn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpportunityCostApiIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void calculatesOpportunityCostThroughWishlistPath() throws Exception {
		mockMvc.perform(get("/api/v1/wishlist/opportunity-cost")
				.queryParam("price", "2000000")
				.queryParam("category", "LIVING"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.originalPrice").value(2_000_000))
			.andExpect(jsonPath("$.sourceCategory").value("LIVING"))
			.andExpect(jsonPath("$.result.itemId").value("ITEM_14"))
			.andExpect(jsonPath("$.result.targetCategory").value("DIGITAL"))
			.andExpect(jsonPath("$.result.calculatedCount").value(1))
			.andExpect(jsonPath("$.result.displayTitle").value("200만 원 상당 최신형 노트북 💻"));
	}

	@Test
	void returnsChickenForReportedMusinsaPrice() throws Exception {
		mockMvc.perform(get("/api/v1/wishlist/opportunity-cost")
				.queryParam("price", "25500")
				.queryParam("category", "FASHION"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result.itemId").value("ITEM_03"))
			.andExpect(jsonPath("$.result.calculatedCount").value(1))
			.andExpect(jsonPath("$.result.displayTitle").value("황금올리브 치킨 🍗"))
			.andExpect(jsonPath("$.result.displayMessage")
				.value("이 상품 1개를 아끼면, 바삭한 황금올리브 치킨을 먹을 수 있어요! 🍗"));
	}

	@Test
	void supportsPlanningPathAlias() throws Exception {
		mockMvc.perform(get("/api/v1/wishes/opportunity-cost")
				.queryParam("price", "35000")
				.queryParam("category", "FASHION"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sourceCategory").value("FASHION"))
			.andExpect(jsonPath("$.result.targetCategory").value(isIn(new String[] {"LIFE", "DIGITAL", "OTHER"})));
	}

	@Test
	void rejectsInvalidPrice() throws Exception {
		mockMvc.perform(get("/api/v1/wishlist/opportunity-cost")
				.queryParam("price", "-1")
				.queryParam("category", "FASHION"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void rejectsMissingPriceWithApiErrorResponse() throws Exception {
		mockMvc.perform(get("/api/v1/wishlist/opportunity-cost")
				.queryParam("category", "FASHION"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
			.andExpect(jsonPath("$.message").value("price is required."));
	}

	@Test
	void rejectsMissingCategoryWithApiErrorResponse() throws Exception {
		mockMvc.perform(get("/api/v1/wishlist/opportunity-cost")
				.queryParam("price", "35000"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"))
			.andExpect(jsonPath("$.message").value("category is required."));
	}
}

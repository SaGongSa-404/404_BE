package com.sagongsa.backend.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DevControllerIntegrationTest extends PostgreSqlContainerTest {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table users cascade");
	}

	@Test
	void devWishCanSubmitStopDecisionWithoutBudgetConflict() throws Exception {
		assertDevWishDecisionFlow("STOP");
	}

	@Test
	void devWishCanSubmitGoDecisionWithoutBudgetConflict() throws Exception {
		assertDevWishDecisionFlow("GO");
	}

	@Test
	void devDecisionHelpersCanCreateDecisionsWithoutExistingBudgetCycle() throws Exception {
		UUID userId = createDevUser();

		mockMvc.perform(post("/api/dev/decisions/test")
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"title": "테스트 헤드폰",
						"price": 99000,
						"result": "GO"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.result").value("GO"))
			.andExpect(jsonPath("$.month").value(YearMonth.now(SEOUL_ZONE).toString()));

		mockMvc.perform(post("/api/dev/decisions/test-batch")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(3));

		assertThat(queryInteger(
			"select count(*) from budget_cycles where user_id = ? and year_month = ?",
			userId,
			YearMonth.now(SEOUL_ZONE).toString()
		)).isOne();
	}

	private void assertDevWishDecisionFlow(String result) throws Exception {
		UUID userId = createDevUser();

		String wishBody = mockMvc.perform(post("/api/dev/wishes/test")
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID itemId = UUID.fromString(objectMapper.readTree(wishBody).get("itemId").asText());

		mockMvc.perform(get("/api/v1/deliberations/items/{itemId}", itemId)
				.header(USER_ID_HEADER, userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.item.id").value(itemId.toString()))
			.andExpect(jsonPath("$.item.status").value("SAVED"));

		mockMvc.perform(post("/api/v1/decisions")
				.header(USER_ID_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionRequest(itemId, result)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.itemId").value(itemId.toString()))
			.andExpect(jsonPath("$.itemStatus").value(result))
			.andExpect(jsonPath("$.result").value(result));

		assertThat(queryInteger(
			"select count(*) from budget_cycles where user_id = ? and year_month = ?",
			userId,
			YearMonth.now(SEOUL_ZONE).toString()
		)).isOne();
	}

	private UUID createDevUser() throws Exception {
		String userBody = mockMvc.perform(post("/api/dev/users/test"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID userId = UUID.fromString(objectMapper.readTree(userBody).get("userId").asText());
		assertThat(queryInteger(
			"select count(*) from budget_cycles where user_id = ? and year_month = ?",
			userId,
			YearMonth.now(SEOUL_ZONE).toString()
		)).isOne();
		return userId;
	}

	private String decisionRequest(UUID itemId, String result) {
		return """
			{
				"itemId": "%s",
				"result": "%s",
				"selfCheckAnswers": [
					{"questionCode": "NEED", "answerBoolean": true},
					{"questionCode": "BUDGET", "answerBoolean": true},
					{"questionCode": "ALTERNATIVE", "answerBoolean": false},
					{"questionCode": "DELAY", "answerBoolean": false}
				]
			}
			""".formatted(itemId, result);
	}

	private Integer queryInteger(String sql, Object... args) {
		return jdbcTemplate.queryForObject(sql, Integer.class, args);
	}
}

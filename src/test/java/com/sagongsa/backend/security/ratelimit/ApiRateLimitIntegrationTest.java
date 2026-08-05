package com.sagongsa.backend.security.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiRateLimitIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void limitsUnauthenticatedAuthenticationRequestsBeforeController() throws Exception {
		for (int i = 0; i < 30; i++) {
			mockMvc.perform(get("/api/auth/me")
					.remoteAddress("127.0.0.1")
					.header("X-Forwarded-For", "203.0.113.30"))
				.andExpect(status().isUnauthorized());
		}

		mockMvc.perform(get("/api/auth/me")
				.remoteAddress("127.0.0.1")
				.header("X-Forwarded-For", "203.0.113.30"))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("X-RateLimit-Limit", "30"))
			.andExpect(header().exists("Retry-After"));
	}

	@Test
	void limitsExpensiveRequestBeforeAuthenticationWork() throws Exception {
		for (int i = 0; i < 30; i++) {
			mockMvc.perform(post("/api/v1/items/import-jobs")
					.remoteAddress("127.0.0.1")
					.header("X-Forwarded-For", "203.0.113.31"))
				.andExpect(status().isBadRequest());
		}

		mockMvc.perform(post("/api/v1/items/import-jobs")
				.remoteAddress("127.0.0.1")
				.header("X-Forwarded-For", "203.0.113.31"))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("X-RateLimit-Limit", "30"));
	}
}

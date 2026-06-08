package com.sagongsa.backend.health;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sagongsa.backend.observability.RequestTraceFilter;
import com.sagongsa.backend.support.PostgreSqlContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest extends PostgreSqlContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsPublicHealthStatus() throws Exception {
		mockMvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(header().string(RequestTraceFilter.REQUEST_ID_HEADER,
				matchesPattern("[0-9a-fA-F-]{36}")))
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.service").value("wigul-backend"))
			.andExpect(jsonPath("$.time").exists());
	}

	@Test
	void reusesSafeRequestIdHeader() throws Exception {
		mockMvc.perform(get("/api/health")
				.header(RequestTraceFilter.REQUEST_ID_HEADER, "cbt-test-001"))
			.andExpect(status().isOk())
			.andExpect(header().string(RequestTraceFilter.REQUEST_ID_HEADER, "cbt-test-001"));
	}

	@Test
	void replacesUnsafeRequestIdHeader() throws Exception {
		mockMvc.perform(get("/health")
				.header(RequestTraceFilter.REQUEST_ID_HEADER, "bad id value"))
			.andExpect(status().isOk())
			.andExpect(header().string(RequestTraceFilter.REQUEST_ID_HEADER,
				matchesPattern("[0-9a-fA-F-]{36}")));
	}

	@Test
	void addsRequestIdHeaderToErrorResponses() throws Exception {
		mockMvc.perform(get("/api/auth/me")
				.header(RequestTraceFilter.REQUEST_ID_HEADER, "cbt-error-001"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string(RequestTraceFilter.REQUEST_ID_HEADER, "cbt-error-001"));
	}
}

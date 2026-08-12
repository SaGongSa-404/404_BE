package com.sagongsa.backend.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sagongsa.backend.support.PostgreSqlContainerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
	"app.auth.trusted-user-id-header.enabled=false",
	"app.auth.jwt-secret=test-jwt-secret-for-prod-development-surface-checks",
	"app.auth.allowed-redirect-uri-prefixes=sagongsa404://auth/callback",
	"app.auth.reviewer-token.enabled=false",
	"app.shopping.import.browser-fetch.enabled=true"
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class ProdDevelopmentSurfaceSecurityIntegrationTest extends PostgreSqlContainerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void productionKeepsPublicAppAvailable() throws Exception {
		mockMvc.perform(get("/app.html"))
			.andExpect(status().isOk());
	}

	@Test
	void productionDeniesDevelopmentHtmlEvenForAuthenticatedUsers() throws Exception {
		for (String path : new String[] {
			"/test-social-feed.html",
			"/test-mypage.html",
			"/deliberation.html"
		}) {
			mockMvc.perform(get(path).with(jwt()))
				.andExpect(status().isForbidden());
		}
	}

	@Test
	void productionDeniesApiDocsAndDevelopmentApiEvenForAuthenticatedUsers() throws Exception {
		mockMvc.perform(get("/v3/api-docs").with(jwt()))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/dev/users/test").with(jwt()))
			.andExpect(status().isForbidden());
	}
}

package com.example.oauthsocialtest.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.oauthsocialtest.auth.JwtTokenService;
import com.example.oauthsocialtest.auth.SocialUserProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private JwtTokenService jwtTokenService;

	@Test
	void returnsAuthenticatedUserForBearerToken() throws Exception {
		mockMvc.perform(get("/api/auth/me").with(jwt().jwt(jwt -> jwt
			.subject("google:google-123")
			.claim("provider", "google")
			.claim("providerUserId", "google-123")
			.claim("name", "Google Tester")
			.claim("email", "google@test.dev")
			.claim("profileImageUrl", "https://example.com/profile.png")
			.claim("authorities", List.of("ROLE_USER"))
		)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authenticated").value(true))
			.andExpect(jsonPath("$.provider").value("google"))
			.andExpect(jsonPath("$.providerUserId").value("google-123"))
			.andExpect(jsonPath("$.email").value("google@test.dev"));
	}

	@Test
	void refreshesAccessToken() throws Exception {
		given(jwtTokenService.refresh(anyString())).willReturn(new JwtTokenService.TokenPair(
			"Bearer",
			"new-access-token",
			Instant.parse("2026-04-13T10:00:00Z"),
			"new-refresh-token",
			Instant.parse("2026-05-13T10:00:00Z"),
			new SocialUserProfile("google", "google-123", "Google Tester", "google@test.dev", null, Map.of())
		));

		mockMvc.perform(post("/api/auth/token/refresh")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"refreshToken":"old-refresh-token"}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.accessToken").value("new-access-token"))
			.andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
	}
}

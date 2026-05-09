package com.sagongsa.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.config.AppAuthProperties;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppRedirectUriSupportTest {

	private final AppRedirectUriSupport support = new AppRedirectUriSupport(testProperties());

	@Test
	void acceptsCustomSchemeRedirectUri() {
		assertThat(support.parseAllowedRedirectUri("sagongsa404://auth/callback"))
			.contains(URI.create("sagongsa404://auth/callback"));
	}

	@Test
	void rejectsExternalHttpsRedirectUri() {
		assertThat(support.parseAllowedRedirectUri("https://example.com/oauth/callback")).isEmpty();
	}

	@Test
	void rejectsLookalikeLocalhostRedirectUri() {
		assertThat(support.parseAllowedRedirectUri("http://localhost.evil.com/oauth/callback")).isEmpty();
	}

	@Test
	void rejectsLocalhostRedirectOutsideAllowedCallbackPath() {
		assertThat(support.parseAllowedRedirectUri("http://localhost/oauth/callback")).isEmpty();
	}

	@Test
	void acceptsLocalhostRedirectOnAllowedCallbackPath() {
		assertThat(support.parseAllowedRedirectUri("http://localhost/auth/callback"))
			.contains(URI.create("http://localhost/auth/callback"));
	}

	@Test
	void buildsSuccessRedirectIntoFragment() {
		JwtTokenService.TokenPair tokenPair = new JwtTokenService.TokenPair(
			"Bearer",
			"access-token",
			Instant.parse("2026-04-13T10:00:00Z"),
			"refresh-token",
			Instant.parse("2026-05-13T10:00:00Z"),
			new SocialUserProfile(
				"google",
				"google-123",
				"Google Tester",
				"google@test.dev",
				"https://example.com/profile.png",
				Map.of(),
				UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
			)
		);

		URI redirectUri = support.buildSuccessRedirectUri(URI.create("sagongsa404://auth/callback"), tokenPair);

		assertThat(redirectUri.toString()).contains("#");
		assertThat(redirectUri.getFragment()).contains("access_token=access-token");
		assertThat(redirectUri.getFragment()).contains("user_id=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
		assertThat(redirectUri.getFragment()).contains("provider=google");
	}

	private static AppAuthProperties testProperties() {
		AppAuthProperties properties = new AppAuthProperties();
		properties.setAllowedRedirectUriPrefixes(java.util.List.of(
			"sagongsa404://auth/callback",
			"http://localhost/auth/callback",
			"http://127.0.0.1/auth/callback",
			"https://localhost/auth/callback",
			"https://127.0.0.1/auth/callback"
		));
		return properties;
	}
}

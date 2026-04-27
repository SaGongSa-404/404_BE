package com.sagongsa.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SocialUserProfileTest {

	@Test
	void createsProfileFromGoogleAttributes() {
		Map<String, Object> attributes = Map.of(
			"sub", "google-123",
			"name", "Google Tester",
			"email", "google@test.dev",
			"picture", "https://example.com/google.png"
		);

		SocialUserProfile profile = SocialUserProfile.from("google", attributes);

		assertThat(profile.provider()).isEqualTo("google");
		assertThat(profile.providerUserId()).isEqualTo("google-123");
		assertThat(profile.name()).isEqualTo("Google Tester");
		assertThat(profile.email()).isEqualTo("google@test.dev");
		assertThat(profile.profileImageUrl()).isEqualTo("https://example.com/google.png");
		assertThat(profile.userId()).isNull();
	}

	@Test
	void createsProfileFromKakaoAttributes() {
		Map<String, Object> attributes = Map.of(
			"id", 987654321L,
			"kakao_account", Map.of(
				"email", "kakao@test.dev",
				"profile", Map.of(
					"nickname", "Kakao Tester",
					"profile_image_url", "https://example.com/kakao.png"
				)
			)
		);

		SocialUserProfile profile = SocialUserProfile.from("kakao", attributes);

		assertThat(profile.provider()).isEqualTo("kakao");
		assertThat(profile.providerUserId()).isEqualTo("987654321");
		assertThat(profile.name()).isEqualTo("Kakao Tester");
		assertThat(profile.email()).isEqualTo("kakao@test.dev");
		assertThat(profile.profileImageUrl()).isEqualTo("https://example.com/kakao.png");
		assertThat(profile.userId()).isNull();
	}

	@Test
	void restoresUserIdFromTokenClaims() {
		UUID userId = UUID.randomUUID();

		SocialUserProfile profile = SocialUserProfile.fromTokenClaims(Map.of(
			"userId", userId.toString(),
			"provider", "google",
			"providerUserId", "google-123",
			"name", "Google Tester"
		));

		assertThat(profile.userId()).isEqualTo(userId);
	}
}

package com.example.oauthsocialtest.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
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
	}

	@Test
	void createsProfileFromNaverAttributes() {
		Map<String, Object> attributes = Map.of(
			"resultcode", "00",
			"message", "success",
			"response", Map.of(
				"id", "naver-123",
				"name", "Naver Tester",
				"email", "naver@test.dev",
				"profile_image", "https://example.com/naver.png"
			)
		);

		SocialUserProfile profile = SocialUserProfile.from("naver", attributes);

		assertThat(profile.provider()).isEqualTo("naver");
		assertThat(profile.providerUserId()).isEqualTo("naver-123");
		assertThat(profile.name()).isEqualTo("Naver Tester");
		assertThat(profile.email()).isEqualTo("naver@test.dev");
		assertThat(profile.profileImageUrl()).isEqualTo("https://example.com/naver.png");
	}

	@Test
	void createsProfileFromAppleAttributes() {
		Map<String, Object> attributes = Map.of(
			"sub", "apple-user-123",
			"email", "apple@test.dev",
			"email_verified", true
		);

		SocialUserProfile profile = SocialUserProfile.from("apple", attributes);

		assertThat(profile.provider()).isEqualTo("apple");
		assertThat(profile.providerUserId()).isEqualTo("apple-user-123");
		assertThat(profile.name()).isEqualTo("apple@test.dev");
		assertThat(profile.email()).isEqualTo("apple@test.dev");
		assertThat(profile.profileImageUrl()).isNull();
	}
}

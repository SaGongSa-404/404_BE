package com.example.oauthsocialtest.auth;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record SocialUserProfile(
	String provider,
	String providerUserId,
	String name,
	String email,
	String profileImageUrl,
	Map<String, Object> rawAttributes
) {

	public static SocialUserProfile from(String registrationId, Map<String, Object> attributes) {
		return switch (registrationId) {
			case "google" -> fromGoogle(attributes);
			case "kakao" -> fromKakao(attributes);
			case "naver" -> fromNaver(attributes);
			case "apple" -> fromApple(attributes);
			default -> throw new IllegalArgumentException("Unsupported provider: " + registrationId);
		};
	}

	private static SocialUserProfile fromGoogle(Map<String, Object> attributes) {
		return new SocialUserProfile(
			"google",
			requiredString(attributes, "sub"),
			firstNonBlank(stringValue(attributes.get("name")), stringValue(attributes.get("given_name")), "Google User"),
			stringValue(attributes.get("email")),
			stringValue(attributes.get("picture")),
			Collections.unmodifiableMap(attributes)
		);
	}

	@SuppressWarnings("unchecked")
	private static SocialUserProfile fromKakao(Map<String, Object> attributes) {
		Map<String, Object> kakaoAccount = asMap(attributes.get("kakao_account"));
		Map<String, Object> profile = asMap(kakaoAccount.get("profile"));

		return new SocialUserProfile(
			"kakao",
			requiredString(attributes, "id"),
			firstNonBlank(
				stringValue(profile.get("nickname")),
				stringValue(attributes.get("properties_nickname")),
				"Kakao User"
			),
			stringValue(kakaoAccount.get("email")),
			firstNonBlank(stringValue(profile.get("profile_image_url")), stringValue(profile.get("thumbnail_image_url"))),
			Collections.unmodifiableMap(attributes)
		);
	}

	private static SocialUserProfile fromNaver(Map<String, Object> attributes) {
		Map<String, Object> response = asMap(attributes.get("response"));

		return new SocialUserProfile(
			"naver",
			requiredString(response, "id"),
			firstNonBlank(
				stringValue(response.get("name")),
				stringValue(response.get("nickname")),
				"Naver User"
			),
			stringValue(response.get("email")),
			stringValue(response.get("profile_image")),
			Collections.unmodifiableMap(attributes)
		);
	}

	private static SocialUserProfile fromApple(Map<String, Object> attributes) {
		return new SocialUserProfile(
			"apple",
			requiredString(attributes, "sub"),
			firstNonBlank(
				stringValue(attributes.get("name")),
				stringValue(attributes.get("email")),
				"Apple User"
			),
			stringValue(attributes.get("email")),
			null,
			Collections.unmodifiableMap(attributes)
		);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> asMap(Object value) {
		if (value instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		return Map.of();
	}

	private static String requiredString(Map<String, Object> attributes, String key) {
		String value = stringValue(attributes.get(key));
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing required attribute: " + key);
		}
		return value;
	}

	private static String stringValue(Object value) {
		return value == null ? null : Objects.toString(value, null);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}
}

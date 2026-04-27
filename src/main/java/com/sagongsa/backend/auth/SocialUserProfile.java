package com.sagongsa.backend.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record SocialUserProfile(
	String provider,
	String providerUserId,
	String name,
	String email,
	String profileImageUrl,
	Map<String, Object> rawAttributes,
	UUID userId
) {

	public static SocialUserProfile from(String registrationId, Map<String, Object> attributes) {
		return switch (registrationId) {
			case "google" -> fromGoogle(attributes);
			case "kakao" -> fromKakao(attributes);
			default -> throw new IllegalArgumentException("Unsupported provider: " + registrationId);
		};
	}

	public static SocialUserProfile fromTokenClaims(Map<String, Object> claims) {
		Map<String, Object> rawClaims = new LinkedHashMap<>(claims);
		return new SocialUserProfile(
			requiredString(rawClaims, "provider"),
			requiredString(rawClaims, "providerUserId"),
			firstNonBlank(stringValue(rawClaims.get("name")), "Social User"),
			stringValue(rawClaims.get("email")),
			stringValue(rawClaims.get("profileImageUrl")),
			Collections.unmodifiableMap(rawClaims),
			uuidValue(rawClaims.get("userId"))
		);
	}

	private static SocialUserProfile fromGoogle(Map<String, Object> attributes) {
		return new SocialUserProfile(
			"google",
			requiredString(attributes, "sub"),
			firstNonBlank(stringValue(attributes.get("name")), stringValue(attributes.get("given_name")), "Google User"),
			stringValue(attributes.get("email")),
			stringValue(attributes.get("picture")),
			Collections.unmodifiableMap(attributes),
			null
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
			Collections.unmodifiableMap(attributes),
			null
		);
	}

	public SocialUserProfile withUserId(UUID userId) {
		return new SocialUserProfile(provider, providerUserId, name, email, profileImageUrl, rawAttributes, userId);
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

	private static UUID uuidValue(Object value) {
		String stringValue = stringValue(value);
		if (stringValue == null || stringValue.isBlank()) {
			return null;
		}
		return UUID.fromString(stringValue);
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

package com.example.oauthsocialtest.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Component
public class AppRedirectUriSupport {

	public static final String REDIRECT_URI_PARAMETER = "redirect_uri";

	private static final String SESSION_KEY = "app.oauth2.redirect_uri";
	private static final Set<String> LOCAL_WEB_HOSTS = Set.of("localhost", "127.0.0.1");

	public void captureRedirectUri(HttpServletRequest request) {
		String redirectUri = request.getParameter(REDIRECT_URI_PARAMETER);
		HttpSession session = request.getSession(true);

		if (!StringUtils.hasText(redirectUri)) {
			session.removeAttribute(SESSION_KEY);
			return;
		}

		parseAllowedRedirectUri(redirectUri).ifPresentOrElse(
			uri -> session.setAttribute(SESSION_KEY, uri.toString()),
			() -> session.removeAttribute(SESSION_KEY)
		);
	}

	public Optional<URI> consumeRedirectUri(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null) {
			return Optional.empty();
		}

		Object value = session.getAttribute(SESSION_KEY);
		session.removeAttribute(SESSION_KEY);

		if (!(value instanceof String redirectUri)) {
			return Optional.empty();
		}

		return parseAllowedRedirectUri(redirectUri);
	}

	public Optional<URI> parseAllowedRedirectUri(String redirectUri) {
		if (!StringUtils.hasText(redirectUri)) {
			return Optional.empty();
		}

		try {
			URI uri = URI.create(redirectUri.trim());
			String scheme = normalize(uri.getScheme());
			if (!StringUtils.hasText(scheme)) {
				return Optional.empty();
			}

			if ("http".equals(scheme) || "https".equals(scheme)) {
				String host = normalize(uri.getHost());
				if (!LOCAL_WEB_HOSTS.contains(host)) {
					return Optional.empty();
				}
			}

			return Optional.of(uri);
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	public URI buildSuccessRedirectUri(URI baseUri, JwtTokenService.TokenPair tokenPair) {
		Map<String, String> fragmentValues = new LinkedHashMap<>();
		fragmentValues.put("access_token", tokenPair.accessToken());
		fragmentValues.put("refresh_token", tokenPair.refreshToken());
		fragmentValues.put("token_type", tokenPair.tokenType());
		fragmentValues.put("access_token_expires_at", tokenPair.accessTokenExpiresAt().toString());
		fragmentValues.put("refresh_token_expires_at", tokenPair.refreshTokenExpiresAt().toString());
		fragmentValues.put("provider", tokenPair.profile().provider());
		fragmentValues.put("provider_user_id", tokenPair.profile().providerUserId());
		fragmentValues.put("name", tokenPair.profile().name());
		fragmentValues.put("email", tokenPair.profile().email());
		fragmentValues.put("profile_image_url", tokenPair.profile().profileImageUrl());
		return appendFragment(baseUri, fragmentValues);
	}

	public URI buildFailureRedirectUri(URI baseUri, String errorCode) {
		return appendFragment(baseUri, Map.of(
			"error", errorCode,
			"token_type", "Bearer"
		));
	}

	private URI appendFragment(URI baseUri, Map<String, String> params) {
		String fragment = params.entrySet().stream()
			.filter(entry -> StringUtils.hasText(entry.getValue()))
			.map(entry -> UriUtils.encodeQueryParam(entry.getKey(), StandardCharsets.UTF_8)
				+ "="
				+ UriUtils.encodeQueryParam(entry.getValue(), StandardCharsets.UTF_8))
			.collect(Collectors.joining("&"));

		return UriComponentsBuilder.fromUri(baseUri)
			.fragment(fragment)
			.build(true)
			.toUri();
	}

	private String normalize(String value) {
		return value == null ? null : value.toLowerCase();
	}
}

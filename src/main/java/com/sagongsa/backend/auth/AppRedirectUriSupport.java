package com.sagongsa.backend.auth;

import com.sagongsa.backend.config.AppAuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@Component
public class AppRedirectUriSupport {

	public static final String REDIRECT_URI_PARAMETER = "redirect_uri";

	private static final String SESSION_KEY = "app.oauth2.redirect_uri";
	private final AppAuthProperties appAuthProperties;

	public AppRedirectUriSupport(AppAuthProperties appAuthProperties) {
		this.appAuthProperties = appAuthProperties;
	}

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

			List<String> allowedPrefixes = appAuthProperties.getAllowedRedirectUriPrefixes();
			boolean allowed = allowedPrefixes.stream()
				.filter(StringUtils::hasText)
				.map(this::parseAllowedPrefix)
				.flatMap(Optional::stream)
				.anyMatch(allowedUri -> matchesAllowedRedirect(uri, allowedUri));

			if (!allowed) {
				return Optional.empty();
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
		fragmentValues.put("user_id", tokenPair.profile().userId() != null ? tokenPair.profile().userId().toString() : null);
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

	private Optional<URI> parseAllowedPrefix(String value) {
		try {
			return Optional.of(URI.create(value.trim()));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private boolean matchesAllowedRedirect(URI requestedUri, URI allowedUri) {
		if (!sameText(requestedUri.getScheme(), allowedUri.getScheme())) {
			return false;
		}
		if (allowedUri.getHost() != null && !sameText(requestedUri.getHost(), allowedUri.getHost())) {
			return false;
		}
		if (allowedUri.getPort() != -1 && requestedUri.getPort() != allowedUri.getPort()) {
			return false;
		}
		if (allowedUri.getHost() == null && allowedUri.getAuthority() != null
			&& !sameText(requestedUri.getAuthority(), allowedUri.getAuthority())) {
			return false;
		}

		String allowedPath = Optional.ofNullable(allowedUri.getPath()).orElse("");
		String requestedPath = Optional.ofNullable(requestedUri.getPath()).orElse("");
		return allowedPath.isBlank()
			|| "/".equals(allowedPath)
			|| requestedPath.equals(allowedPath)
			|| requestedPath.startsWith(allowedPath.endsWith("/") ? allowedPath : allowedPath + "/");
	}

	private boolean sameText(String first, String second) {
		return first != null && second != null && normalize(first).equals(normalize(second));
	}
}

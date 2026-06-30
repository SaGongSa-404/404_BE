package com.sagongsa.backend.config;

import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PublicServerSecurityGuard implements ApplicationRunner {

	private static final String DEFAULT_JWT_SECRET = "local-development-jwt-secret-change-me";
	private static final int MIN_SECRET_LENGTH = 32;
	private static final int MAX_SHOPPING_RESPONSE_BYTES = 3_000_000;
	private static final Duration MAX_BROWSER_FETCH_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration MAX_BROWSER_RENDER_WAIT = Duration.ofSeconds(5);
	private static final Duration MAX_BROWSER_NETWORK_IDLE_TIMEOUT = Duration.ofSeconds(2);

	private final AppAuthProperties authProperties;
	private final ShoppingImportProperties shoppingImportProperties;
	private final Environment environment;
	private final boolean trustedHeaderEnabled;

	public PublicServerSecurityGuard(
		AppAuthProperties authProperties,
		ShoppingImportProperties shoppingImportProperties,
		Environment environment,
		@Value("${app.auth.trusted-user-id-header.enabled:false}") boolean trustedHeaderEnabled
	) {
		this.authProperties = authProperties;
		this.shoppingImportProperties = shoppingImportProperties;
		this.environment = environment;
		this.trustedHeaderEnabled = trustedHeaderEnabled;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!environment.acceptsProfiles(Profiles.of("prod"))) {
			return;
		}

		requireStrongSecret("APP_JWT_SECRET", authProperties.getJwtSecret());
		if (DEFAULT_JWT_SECRET.equals(authProperties.getJwtSecret())) {
			throw new IllegalStateException("APP_JWT_SECRET must not use the development default in prod");
		}
		if (trustedHeaderEnabled) {
			throw new IllegalStateException("app.auth.trusted-user-id-header.enabled must be false in prod");
		}
		if (hasLocalRedirectPrefix(authProperties.getAllowedRedirectUriPrefixes())) {
			throw new IllegalStateException("app.auth.allowed-redirect-uri-prefixes must not include localhost in prod");
		}
		validateShoppingImportSettings();
		if (authProperties.getReviewerToken().isEnabled()) {
			requireStrongSecret("APP_REVIEWER_TOKEN_SECRET", authProperties.getReviewerToken().getSecret());
		}
	}

	private void requireStrongSecret(String name, String value) {
		if (!StringUtils.hasText(value) || value.length() < MIN_SECRET_LENGTH) {
			throw new IllegalStateException(name + " must be set to at least " + MIN_SECRET_LENGTH + " characters in prod");
		}
	}

	private boolean hasLocalRedirectPrefix(List<String> prefixes) {
		return prefixes.stream().anyMatch(this::isLocalRedirectPrefix);
	}

	private void validateShoppingImportSettings() {
		if (!shoppingImportProperties.getBrowserFetch().isEnabled()) {
			throw new IllegalStateException("app.shopping.import.browser-fetch.enabled must be true for private test prod");
		}
		if (shoppingImportProperties.getMaxResponseBytes() > MAX_SHOPPING_RESPONSE_BYTES) {
			throw new IllegalStateException("app.shopping.import.max-response-bytes must be at most " + MAX_SHOPPING_RESPONSE_BYTES + " in prod");
		}

		ShoppingImportProperties.BrowserFetch browserFetch = shoppingImportProperties.getBrowserFetch();
		requireDurationAtMost("app.shopping.import.browser-fetch.timeout", browserFetch.getTimeout(), MAX_BROWSER_FETCH_TIMEOUT);
		requireDurationAtMost("app.shopping.import.browser-fetch.render-wait", browserFetch.getRenderWait(), MAX_BROWSER_RENDER_WAIT);
		requireDurationAtMost(
			"app.shopping.import.browser-fetch.network-idle-timeout",
			browserFetch.getNetworkIdleTimeout(),
			MAX_BROWSER_NETWORK_IDLE_TIMEOUT
		);
	}

	private void requireDurationAtMost(String name, Duration value, Duration max) {
		if (value != null && value.compareTo(max) > 0) {
			throw new IllegalStateException(name + " must be at most " + max + " in prod");
		}
	}

	private boolean isLocalRedirectPrefix(String prefix) {
		try {
			URI uri = URI.create(prefix);
			String host = uri.getHost();
			if (host == null) {
				return false;
			}
			String normalizedHost = host.toLowerCase();
			return "localhost".equals(normalizedHost)
				|| normalizedHost.endsWith(".localhost")
				|| normalizedHost.startsWith("127.")
				|| "0.0.0.0".equals(normalizedHost)
				|| "::1".equals(normalizedHost);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("app.auth.allowed-redirect-uri-prefixes contains an invalid URI: " + prefix, exception);
		}
	}
}

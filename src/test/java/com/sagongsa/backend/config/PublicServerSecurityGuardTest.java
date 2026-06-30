package com.sagongsa.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sagongsa.backend.itemimport.item.ShoppingImportProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PublicServerSecurityGuardTest {

	@Test
	void acceptsPrivateTestSafeProdSettings() {
		AppAuthProperties authProperties = safeAuthProperties();
		ShoppingImportProperties shoppingImportProperties = safeShoppingImportProperties();

		assertThatCode(() -> guard(authProperties, shoppingImportProperties, false).run(null))
			.doesNotThrowAnyException();
	}

	@Test
	void rejectsDefaultJwtSecretInProd() {
		AppAuthProperties authProperties = safeAuthProperties();
		authProperties.setJwtSecret("local-development-jwt-secret-change-me");

		assertThatThrownBy(() -> guard(authProperties, safeShoppingImportProperties(), false).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("development default");
	}

	@Test
	void rejectsReviewerTokenWithoutStrongSecretInProd() {
		AppAuthProperties authProperties = safeAuthProperties();
		authProperties.getReviewerToken().setSecret("");

		assertThatThrownBy(() -> guard(authProperties, safeShoppingImportProperties(), false).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("APP_REVIEWER_TOKEN_SECRET");
	}

	@Test
	void rejectsTrustedHeaderInProd() {
		assertThatThrownBy(() -> guard(safeAuthProperties(), safeShoppingImportProperties(), true).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("trusted-user-id-header");
	}

	@Test
	void rejectsLocalRedirectPrefixInProd() {
		AppAuthProperties authProperties = safeAuthProperties();
		authProperties.setAllowedRedirectUriPrefixes(List.of("sagongsa404://auth/callback", "http://localhost/auth/callback"));

		assertThatThrownBy(() -> guard(authProperties, safeShoppingImportProperties(), false).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("localhost");
	}

	@Test
	void rejectsDisabledBrowserFetchInPrivateTestProd() {
		ShoppingImportProperties shoppingImportProperties = safeShoppingImportProperties();
		shoppingImportProperties.getBrowserFetch().setEnabled(false);

		assertThatThrownBy(() -> guard(safeAuthProperties(), shoppingImportProperties, false).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("browser-fetch");
	}

	@Test
	void rejectsOversizedShoppingImportResponseLimitInProd() {
		ShoppingImportProperties shoppingImportProperties = safeShoppingImportProperties();
		shoppingImportProperties.setMaxResponseBytes(3_000_001);

		assertThatThrownBy(() -> guard(safeAuthProperties(), shoppingImportProperties, false).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("max-response-bytes");
	}

	private PublicServerSecurityGuard guard(
		AppAuthProperties authProperties,
		ShoppingImportProperties shoppingImportProperties,
		boolean trustedHeaderEnabled
	) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		return new PublicServerSecurityGuard(authProperties, shoppingImportProperties, environment, trustedHeaderEnabled);
	}

	private AppAuthProperties safeAuthProperties() {
		AppAuthProperties authProperties = new AppAuthProperties();
		authProperties.setJwtSecret("private-test-jwt-secret-with-strong-length");
		authProperties.setAllowedRedirectUriPrefixes(List.of("sagongsa404://auth/callback"));
		authProperties.getReviewerToken().setSecret("private-test-reviewer-token-secret-long");
		return authProperties;
	}

	private ShoppingImportProperties safeShoppingImportProperties() {
		ShoppingImportProperties properties = new ShoppingImportProperties();
		properties.getBrowserFetch().setEnabled(true);
		return properties;
	}
}

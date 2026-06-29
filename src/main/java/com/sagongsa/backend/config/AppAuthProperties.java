package com.sagongsa.backend.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AppAuthProperties {

	private String issuer = "404-backend";
	private String jwtSecret;
	private Duration accessTokenTtl = Duration.ofHours(2);
	private Duration refreshTokenTtl = Duration.ofDays(30);
	private List<String> allowedRedirectUriPrefixes = new ArrayList<>();
	private final ReviewerToken reviewerToken = new ReviewerToken();

	public String getIssuer() {
		return issuer;
	}

	public void setIssuer(String issuer) {
		this.issuer = issuer;
	}

	public String getJwtSecret() {
		return jwtSecret;
	}

	public void setJwtSecret(String jwtSecret) {
		this.jwtSecret = jwtSecret;
	}

	public Duration getAccessTokenTtl() {
		return accessTokenTtl;
	}

	public void setAccessTokenTtl(Duration accessTokenTtl) {
		this.accessTokenTtl = accessTokenTtl;
	}

	public Duration getRefreshTokenTtl() {
		return refreshTokenTtl;
	}

	public void setRefreshTokenTtl(Duration refreshTokenTtl) {
		this.refreshTokenTtl = refreshTokenTtl;
	}

	public List<String> getAllowedRedirectUriPrefixes() {
		return allowedRedirectUriPrefixes;
	}

	public void setAllowedRedirectUriPrefixes(List<String> allowedRedirectUriPrefixes) {
		this.allowedRedirectUriPrefixes = allowedRedirectUriPrefixes == null
			? new ArrayList<>()
			: new ArrayList<>(allowedRedirectUriPrefixes);
	}

	public ReviewerToken getReviewerToken() {
		return reviewerToken;
	}

	public static class ReviewerToken {

		private boolean enabled = true;
		private String secret = "";
		private int rateLimitMaxAttempts = 10;
		private Duration rateLimitWindow = Duration.ofMinutes(10);

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getSecret() {
			return secret;
		}

		public void setSecret(String secret) {
			this.secret = secret;
		}

		public int getRateLimitMaxAttempts() {
			return rateLimitMaxAttempts;
		}

		public void setRateLimitMaxAttempts(int rateLimitMaxAttempts) {
			this.rateLimitMaxAttempts = rateLimitMaxAttempts;
		}

		public Duration getRateLimitWindow() {
			return rateLimitWindow;
		}

		public void setRateLimitWindow(Duration rateLimitWindow) {
			this.rateLimitWindow = rateLimitWindow;
		}
	}
}

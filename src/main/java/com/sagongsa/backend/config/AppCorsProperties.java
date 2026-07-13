package com.sagongsa.backend.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public class AppCorsProperties {

	private List<String> allowedOrigins = new ArrayList<>();
	private List<String> allowedOriginPatterns = new ArrayList<>();
	private List<String> allowedMethods = new ArrayList<>();
	private List<String> allowedHeaders = new ArrayList<>();
	private List<String> exposedHeaders = new ArrayList<>();
	private boolean allowCredentials;
	private Duration maxAge;

	public List<String> getAllowedOrigins() {
		return allowedOrigins;
	}

	public void setAllowedOrigins(List<String> allowedOrigins) {
		this.allowedOrigins = copy(allowedOrigins);
	}

	public List<String> getAllowedOriginPatterns() {
		return allowedOriginPatterns;
	}

	public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
		this.allowedOriginPatterns = copy(allowedOriginPatterns);
	}

	public List<String> getAllowedMethods() {
		return allowedMethods;
	}

	public void setAllowedMethods(List<String> allowedMethods) {
		this.allowedMethods = copy(allowedMethods);
	}

	public List<String> getAllowedHeaders() {
		return allowedHeaders;
	}

	public void setAllowedHeaders(List<String> allowedHeaders) {
		this.allowedHeaders = copy(allowedHeaders);
	}

	public List<String> getExposedHeaders() {
		return exposedHeaders;
	}

	public void setExposedHeaders(List<String> exposedHeaders) {
		this.exposedHeaders = copy(exposedHeaders);
	}

	public boolean isAllowCredentials() {
		return allowCredentials;
	}

	public void setAllowCredentials(boolean allowCredentials) {
		this.allowCredentials = allowCredentials;
	}

	public Duration getMaxAge() {
		return maxAge;
	}

	public void setMaxAge(Duration maxAge) {
		this.maxAge = maxAge;
	}

	private static List<String> copy(List<String> values) {
		return values == null ? new ArrayList<>() : new ArrayList<>(values);
	}
}

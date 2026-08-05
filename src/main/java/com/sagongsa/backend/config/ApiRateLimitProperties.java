package com.sagongsa.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.rate-limit")
public class ApiRateLimitProperties {

	private boolean enabled = true;
	private int maxTrackedKeys = 10_000;
	private final Policy api = new Policy(600, Duration.ofMinutes(1));
	private final Policy authentication = new Policy(30, Duration.ofMinutes(1));
	private final Policy expensiveRequest = new Policy(30, Duration.ofMinutes(1));
	private final Policy health = new Policy(120, Duration.ofMinutes(1));

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getMaxTrackedKeys() {
		return maxTrackedKeys;
	}

	public void setMaxTrackedKeys(int maxTrackedKeys) {
		this.maxTrackedKeys = maxTrackedKeys;
	}

	public Policy getApi() {
		return api;
	}

	public Policy getAuthentication() {
		return authentication;
	}

	public Policy getExpensiveRequest() {
		return expensiveRequest;
	}

	public Policy getHealth() {
		return health;
	}

	public static class Policy {

		private int maxRequests;
		private Duration window;

		public Policy() {
		}

		Policy(int maxRequests, Duration window) {
			this.maxRequests = maxRequests;
			this.window = window;
		}

		public int getMaxRequests() {
			return maxRequests;
		}

		public void setMaxRequests(int maxRequests) {
			this.maxRequests = maxRequests;
		}

		public Duration getWindow() {
			return window;
		}

		public void setWindow(Duration window) {
			this.window = window;
		}
	}
}

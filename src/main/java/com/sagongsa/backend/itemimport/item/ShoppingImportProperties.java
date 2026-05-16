package com.sagongsa.backend.itemimport.item;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.shopping.import")
public class ShoppingImportProperties {

	private final BrowserFetch browserFetch = new BrowserFetch();

	public BrowserFetch getBrowserFetch() {
		return browserFetch;
	}

	public static class BrowserFetch {

		private static final String DEFAULT_USER_AGENT =
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
				+ "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";

		private boolean enabled;
		private Duration timeout = Duration.ofSeconds(30);
		private Duration renderWait = Duration.ofSeconds(5);
		private Duration networkIdleTimeout = Duration.ofSeconds(2);
		private String userAgent = DEFAULT_USER_AGENT;
		private String locale = "ko-KR";
		private int viewportWidth = 1440;
		private int viewportHeight = 1200;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public Duration getTimeout() {
			return timeout;
		}

		public void setTimeout(Duration timeout) {
			this.timeout = defaultIfNull(timeout, Duration.ofSeconds(30));
		}

		public Duration getRenderWait() {
			return renderWait;
		}

		public void setRenderWait(Duration renderWait) {
			this.renderWait = defaultIfNull(renderWait, Duration.ofSeconds(5));
		}

		public Duration getNetworkIdleTimeout() {
			return networkIdleTimeout;
		}

		public void setNetworkIdleTimeout(Duration networkIdleTimeout) {
			this.networkIdleTimeout = defaultIfNull(networkIdleTimeout, Duration.ofSeconds(2));
		}

		public String getUserAgent() {
			return userAgent;
		}

		public void setUserAgent(String userAgent) {
			this.userAgent = isBlank(userAgent) ? DEFAULT_USER_AGENT : userAgent;
		}

		public String getLocale() {
			return locale;
		}

		public void setLocale(String locale) {
			this.locale = isBlank(locale) ? "ko-KR" : locale;
		}

		public int getViewportWidth() {
			return viewportWidth;
		}

		public void setViewportWidth(int viewportWidth) {
			this.viewportWidth = viewportWidth <= 0 ? 1440 : viewportWidth;
		}

		public int getViewportHeight() {
			return viewportHeight;
		}

		public void setViewportHeight(int viewportHeight) {
			this.viewportHeight = viewportHeight <= 0 ? 1200 : viewportHeight;
		}

		private Duration defaultIfNull(Duration value, Duration defaultValue) {
			return value == null ? defaultValue : value;
		}

		private boolean isBlank(String value) {
			return value == null || value.isBlank();
		}
	}
}

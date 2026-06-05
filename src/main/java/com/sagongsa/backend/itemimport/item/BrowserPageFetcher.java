package com.sagongsa.backend.itemimport.item;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class BrowserPageFetcher implements PageFetcher, AutoCloseable {

	private static final String ABLY_MOBILE_HOST = "m.a-bly.com";
	private static final String IOS_MOBILE_USER_AGENT =
		"Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
			+ "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

	private final ShoppingImportProperties.BrowserFetch properties;
	private final Object browserLock = new Object();
	private Playwright playwright;
	private Browser browser;

	public BrowserPageFetcher(ShoppingImportProperties.BrowserFetch properties) {
		this.properties = properties;
	}

	@Override
	public FetchedPage fetch(URI uri) {
		ShoppingUrlSafety.validatePublicHost(uri);

		try (BrowserContext context = browser().newContext(contextOptions(uri))) {
			installRequestSafetyGuard(context);
			Page page = context.newPage();
			try {
				Response response = page.navigate(
					uri.toString(),
					new Page.NavigateOptions()
						.setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
						.setTimeout(toMillis(properties.getTimeout()))
				);
				waitForNetworkIdle(page);
				waitForRenderDelay(page);

				URI finalUri = URI.create(page.url());
				ShoppingUrlSafety.validatePublicHost(finalUri);

				return new FetchedPage(
					uri,
					finalUri,
					response == null ? 200 : response.status(),
					response == null ? "text/html" : response.headerValue("content-type"),
					page.content()
				);
			} finally {
				page.close();
			}
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid rendered shopping page url", exception);
		} catch (PlaywrightException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to render shopping page", exception);
		}
	}

	private Browser browser() {
		synchronized (browserLock) {
			if (browser == null) {
				playwright = Playwright.create();
				browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
			}
			return browser;
		}
	}

	private void installRequestSafetyGuard(BrowserContext context) {
		context.route("**/*", route -> {
			String requestUrl = route.request().url();
			try {
				URI requestUri = URI.create(requestUrl);
				String scheme = Optional.ofNullable(requestUri.getScheme()).orElse("").toLowerCase(Locale.ROOT);
				if (!scheme.equals("http") && !scheme.equals("https")) {
					route.resume();
					return;
				}
				ShoppingUrlSafety.validatePublicHost(requestUri);
				route.resume();
			}
			catch (RuntimeException exception) {
				route.abort();
			}
		});
	}

	private Browser.NewContextOptions contextOptions(URI uri) {
		if (isAblyMobileUri(uri)) {
			return new Browser.NewContextOptions()
				.setUserAgent(IOS_MOBILE_USER_AGENT)
				.setLocale(properties.getLocale())
				.setViewportSize(390, 844)
				.setDeviceScaleFactor(3)
				.setIsMobile(true)
				.setHasTouch(true);
		}
		return new Browser.NewContextOptions()
			.setUserAgent(properties.getUserAgent())
			.setLocale(properties.getLocale())
			.setViewportSize(properties.getViewportWidth(), properties.getViewportHeight());
	}

	private boolean isAblyMobileUri(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		return ABLY_MOBILE_HOST.equals(host);
	}

	private void waitForNetworkIdle(Page page) {
		Duration timeout = properties.getNetworkIdleTimeout();
		if (isZeroOrNegative(timeout)) {
			return;
		}

		try {
			page.waitForLoadState(
				LoadState.NETWORKIDLE,
				new Page.WaitForLoadStateOptions().setTimeout(toMillis(timeout))
			);
		} catch (PlaywrightException ignored) {
			// Long-polling pages may never become network-idle; use the rendered DOM collected so far.
		}
	}

	private void waitForRenderDelay(Page page) {
		Duration renderWait = properties.getRenderWait();
		if (!isZeroOrNegative(renderWait)) {
			page.waitForTimeout(toMillis(renderWait));
		}
	}

	private boolean isZeroOrNegative(Duration duration) {
		return duration == null || duration.isZero() || duration.isNegative();
	}

	private double toMillis(Duration duration) {
		if (duration == null || duration.isNegative()) {
			return 0;
		}
		return duration.toMillis();
	}

	@Override
	@PreDestroy
	public void close() {
		synchronized (browserLock) {
			if (browser != null) {
				browser.close();
				browser = null;
			}
			if (playwright != null) {
				playwright.close();
				playwright = null;
			}
		}
	}
}

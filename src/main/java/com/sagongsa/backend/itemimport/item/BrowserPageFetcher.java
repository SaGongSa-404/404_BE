package com.sagongsa.backend.itemimport.item;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class BrowserPageFetcher implements PageFetcher, AutoCloseable {

	private static final String ABLY_MOBILE_HOST = "m.a-bly.com";
	private static final Pattern ABLY_PRODUCT_PRICE_META = Pattern.compile(
		"(?is)<meta[^>]*property\\s*=\\s*[\\\"']product:price:amount[\\\"'][^>]*content\\s*=\\s*[\\\"']\\s*[1-9][0-9,]*"
	);
	private static final String IOS_MOBILE_USER_AGENT =
		"Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
			+ "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

	private final ShoppingImportProperties.BrowserFetch properties;
	private final ShoppingImportProperties.KreamProxy kreamProxy;
	private final ShoppingImportProperties.NaverProxy naverProxy;
	private final int maxResponseBytes;
	private final Supplier<Playwright> playwrightFactory;
	private final Object browserLock = new Object();
	private Playwright playwright;
	private Browser browser;

	public BrowserPageFetcher(ShoppingImportProperties.BrowserFetch properties) {
		this(
			properties,
			Playwright::create,
			1_000_000,
			new ShoppingImportProperties.KreamProxy(),
			new ShoppingImportProperties.NaverProxy()
		);
	}

	public BrowserPageFetcher(ShoppingImportProperties.BrowserFetch properties, int maxResponseBytes) {
		this(
			properties,
			Playwright::create,
			maxResponseBytes,
			new ShoppingImportProperties.KreamProxy(),
			new ShoppingImportProperties.NaverProxy()
		);
	}

	public BrowserPageFetcher(
		ShoppingImportProperties.BrowserFetch properties,
		int maxResponseBytes,
		ShoppingImportProperties.KreamProxy kreamProxy
	) {
		this(properties, Playwright::create, maxResponseBytes, kreamProxy, new ShoppingImportProperties.NaverProxy());
	}

	public BrowserPageFetcher(
		ShoppingImportProperties.BrowserFetch properties,
		int maxResponseBytes,
		ShoppingImportProperties.KreamProxy kreamProxy,
		ShoppingImportProperties.NaverProxy naverProxy
	) {
		this(properties, Playwright::create, maxResponseBytes, kreamProxy, naverProxy);
	}

	BrowserPageFetcher(ShoppingImportProperties.BrowserFetch properties, Supplier<Playwright> playwrightFactory) {
		this(
			properties,
			playwrightFactory,
			1_000_000,
			new ShoppingImportProperties.KreamProxy(),
			new ShoppingImportProperties.NaverProxy()
		);
	}

	BrowserPageFetcher(
		ShoppingImportProperties.BrowserFetch properties,
		Supplier<Playwright> playwrightFactory,
		int maxResponseBytes,
		ShoppingImportProperties.KreamProxy kreamProxy,
		ShoppingImportProperties.NaverProxy naverProxy
	) {
		this.properties = properties;
		this.playwrightFactory = playwrightFactory;
		this.maxResponseBytes = maxResponseBytes <= 0 ? 1_000_000 : maxResponseBytes;
		this.kreamProxy = kreamProxy == null ? new ShoppingImportProperties.KreamProxy() : kreamProxy;
		this.naverProxy = naverProxy == null ? new ShoppingImportProperties.NaverProxy() : naverProxy;
	}

	@Override
	public FetchedPage fetch(URI uri) {
		synchronized (browserLock) {
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
					waitForAblyProductMetadata(page, uri);

					URI finalUri = URI.create(page.url());
					ShoppingUrlSafety.validatePublicHost(finalUri);
					String body = page.content();
					if (body != null && body.getBytes(StandardCharsets.UTF_8).length > maxResponseBytes) {
						throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rendered shopping page response is too large");
					}

					int statusCode = response == null ? 200 : response.status();
					if (isAblyMobileUri(finalUri) && hasAblyProductMetadata(body)) {
						statusCode = 200;
					}

					return new FetchedPage(
						uri,
						finalUri,
						statusCode,
						response == null ? "text/html" : response.headerValue("content-type"),
						body
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
	}

	private Browser browser() {
		synchronized (browserLock) {
			if (browser == null) {
				Playwright newPlaywright = null;
				boolean success = false;
				try {
					newPlaywright = playwrightFactory.get();
					Browser newBrowser = newPlaywright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
					playwright = newPlaywright;
					browser = newBrowser;
					success = true;
				}
				finally {
					if (!success) {
						closeQuietly(newPlaywright);
					}
				}
			}
			return browser;
		}
	}

	private void installRequestSafetyGuard(BrowserContext context) {
		context.route("**/*", BrowserPageFetcher::guardRoute);
	}

	static void guardRoute(Route route) {
		try {
			String requestUrl = route.request().url();
			URI requestUri = URI.create(requestUrl);
			String scheme = Optional.ofNullable(requestUri.getScheme()).orElse("").toLowerCase(Locale.ROOT);
			if (!scheme.equals("http") && !scheme.equals("https")) {
				route.resume();
				return;
			}
			ShoppingUrlSafety.validatePublicHost(requestUri);
			route.resume();
		}
		catch (IllegalArgumentException | ResponseStatusException | PlaywrightException exception) {
			abortQuietly(route);
		}
	}

	private static void abortQuietly(Route route) {
		try {
			route.abort();
		}
		catch (PlaywrightException ignored) {
		}
	}

	Browser.NewContextOptions contextOptions(URI uri) {
		Browser.NewContextOptions options;
		if (isAblyMobileUri(uri)) {
			options = new Browser.NewContextOptions()
				.setUserAgent(IOS_MOBILE_USER_AGENT)
				.setLocale(properties.getLocale())
				.setViewportSize(390, 844)
				.setDeviceScaleFactor(3)
				.setIsMobile(true)
				.setHasTouch(true);
		} else {
			options = new Browser.NewContextOptions()
				.setUserAgent(properties.getUserAgent())
				.setLocale(properties.getLocale())
				.setViewportSize(properties.getViewportWidth(), properties.getViewportHeight());
		}
		if (kreamProxy.isConfigured() && isKreamUri(uri)) {
			options.setProxy(kreamProxy.browserServer());
		} else if (naverProxy.isConfigured() && isNaverShoppingUri(uri)) {
			options.setProxy(naverProxy.browserServer());
		}
		return options;
	}

	private boolean isAblyMobileUri(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		return ABLY_MOBILE_HOST.equals(host);
	}

	private boolean isKreamUri(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		return host.equals("kream.co.kr") || host.endsWith(".kream.co.kr");
	}

	private boolean isNaverShoppingUri(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		return host.equals("naver.me")
			|| host.equals("app.shopping.naver.com")
			|| host.equals("shopping.naver.com")
			|| host.equals("m.shopping.naver.com")
			|| host.equals("smartstore.naver.com")
			|| host.equals("m.smartstore.naver.com")
			|| host.equals("brand.naver.com")
			|| host.equals("m.brand.naver.com");
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

	private void waitForAblyProductMetadata(Page page, URI uri) {
		if (!isAblyMobileUri(uri)) {
			return;
		}
		try {
			page.waitForSelector(
				"meta[property='product:price:amount']",
				new Page.WaitForSelectorOptions().setTimeout(Math.min(toMillis(properties.getTimeout()), 10_000))
			);
		} catch (PlaywrightException ignored) {
			// Keep the challenge page so the caller can report a normal import failure.
		}
	}

	static boolean hasAblyProductMetadata(String body) {
		return body != null && ABLY_PRODUCT_PRICE_META.matcher(body).find();
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

	private void closeQuietly(Playwright playwright) {
		if (playwright == null) {
			return;
		}
		try {
			playwright.close();
		}
		catch (Throwable ignored) {
			// Keep the original browser startup failure visible.
		}
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

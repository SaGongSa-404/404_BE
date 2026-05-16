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
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class BrowserPageFetcher implements PageFetcher {

	private final ShoppingImportProperties.BrowserFetch properties;

	public BrowserPageFetcher(ShoppingImportProperties.BrowserFetch properties) {
		this.properties = properties;
	}

	@Override
	public FetchedPage fetch(URI uri) {
		ShoppingUrlSafety.validatePublicHost(uri);

		try (Playwright playwright = Playwright.create();
			 Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
			 BrowserContext context = browser.newContext(contextOptions())) {
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

	private Browser.NewContextOptions contextOptions() {
		return new Browser.NewContextOptions()
			.setUserAgent(properties.getUserAgent())
			.setLocale(properties.getLocale())
			.setViewportSize(properties.getViewportWidth(), properties.getViewportHeight());
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
}

package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import java.net.URI;
import org.junit.jupiter.api.Test;

class BrowserPageFetcherTest {

	@Test
	void closesPlaywrightWhenBrowserLaunchFailsWithThrowable() {
		ShoppingImportProperties.BrowserFetch properties = new ShoppingImportProperties.BrowserFetch();
		Playwright playwright = mock(Playwright.class);
		BrowserType browserType = mock(BrowserType.class);
		AssertionError failure = new AssertionError("launch failed");

		when(playwright.chromium()).thenReturn(browserType);
		when(browserType.launch(any(BrowserType.LaunchOptions.class))).thenThrow(failure);

		BrowserPageFetcher fetcher = new BrowserPageFetcher(properties, () -> playwright);

		assertThatThrownBy(() -> fetcher.fetch(URI.create("https://1.1.1.1/product")))
			.isSameAs(failure);
		verify(playwright).close();
	}

	@Test
	void abortsRouteWhenRequestUrlCannotBeRead() {
		Route route = mock(Route.class);
		Request request = mock(Request.class);

		when(route.request()).thenReturn(request);
		when(request.url()).thenThrow(new PlaywrightException("context closed"));

		BrowserPageFetcher.guardRoute(route);

		verify(route).abort();
	}

	@Test
	void abortsPrivateNetworkRoute() {
		Route route = mock(Route.class);
		Request request = mock(Request.class);

		when(route.request()).thenReturn(request);
		when(request.url()).thenReturn("http://127.0.0.1/internal");

		BrowserPageFetcher.guardRoute(route);

		verify(route).abort();
	}
}

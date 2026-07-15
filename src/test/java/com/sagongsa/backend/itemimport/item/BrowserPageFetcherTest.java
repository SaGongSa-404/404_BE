package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;
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
	void appliesEachConfiguredProxyOnlyToItsShoppingContext() {
		ShoppingImportProperties.BrowserFetch browserFetch = new ShoppingImportProperties.BrowserFetch();
		ShoppingImportProperties.KreamProxy kreamProxy = new ShoppingImportProperties.KreamProxy();
		kreamProxy.setEnabled(true);
		kreamProxy.setType(ShoppingImportProperties.KreamProxy.Type.SOCKS);
		kreamProxy.setHost("127.0.0.1");
		kreamProxy.setPort(18080);
		ShoppingImportProperties.NaverProxy naverProxy = new ShoppingImportProperties.NaverProxy();
		naverProxy.setEnabled(true);
		naverProxy.setType(ShoppingImportProperties.NaverProxy.Type.HTTP);
		naverProxy.setHost("naver-proxy.internal");
		naverProxy.setPort(3128);
		BrowserPageFetcher fetcher = new BrowserPageFetcher(browserFetch, 1_000_000, kreamProxy, naverProxy);

		assertThat(fetcher.contextOptions(URI.create("https://kream.co.kr/products/444045")).proxy.server)
			.isEqualTo("socks5://127.0.0.1:18080");
		assertThat(fetcher.contextOptions(URI.create("https://naver.me/5imjsySn")).proxy.server)
			.isEqualTo("http://naver-proxy.internal:3128");
		assertThat(fetcher.contextOptions(URI.create("https://m.smartstore.naver.com/edithshop/products/13581698411")).proxy.server)
			.isEqualTo("http://naver-proxy.internal:3128");
		assertThat(fetcher.contextOptions(URI.create("https://zigzag.kr/catalog/products/1")).proxy)
			.isNull();
	}

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

	@Test
	void recognizesRenderedAblyProductMetadataOnlyForPositivePrice() {
		assertThat(BrowserPageFetcher.hasAblyProductMetadata(
			"<meta property=\"product:price:amount\" content=\"52,110\">"
		)).isTrue();
		assertThat(BrowserPageFetcher.hasAblyProductMetadata(
			"<meta property=\"product:price:amount\" content=\"0\">"
		)).isFalse();
		assertThat(BrowserPageFetcher.hasAblyProductMetadata("<title>보안 확인 중..</title>")).isFalse();
	}
}

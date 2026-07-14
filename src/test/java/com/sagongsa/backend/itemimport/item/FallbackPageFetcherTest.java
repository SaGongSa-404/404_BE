package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FallbackPageFetcherTest {

	@Test
	void usesFallbackWhenPrimaryReturnsForbidden() {
		URI uri = URI.create("https://shop.example.com/products/1");
		FallbackPageFetcher fetcher = new FallbackPageFetcher(
			requestedUri -> new FetchedPage(requestedUri, requestedUri, 403, "text/html", "<html>blocked</html>"),
			requestedUri -> new FetchedPage(requestedUri, requestedUri, 200, "text/html", "<html>rendered</html>")
		);

		FetchedPage page = fetcher.fetch(uri);

		assertThat(page.statusCode()).isEqualTo(200);
		assertThat(page.body()).contains("rendered");
	}

	@Test
	void keepsPrimaryResponseWhenPrimarySucceeds() {
		URI uri = URI.create("https://shop.example.com/products/1");
		FallbackPageFetcher fetcher = new FallbackPageFetcher(
			requestedUri -> new FetchedPage(requestedUri, requestedUri, 200, "text/html", "<html>primary</html>"),
			requestedUri -> new FetchedPage(requestedUri, requestedUri, 200, "text/html", "<html>fallback</html>")
		);

		FetchedPage page = fetcher.fetch(uri);

		assertThat(page.body()).contains("primary");
	}

	@Test
	void usesNaverFinalProductUrlWhenPrimaryNaverProductIsRateLimited() {
		URI originalUri = URI.create("https://naver.me/5Au2I5Ev");
		URI finalUri = URI.create("https://m.brand.naver.com/cookierun/products/13194003181?tr=nshfum");
		AtomicReference<URI> fallbackRequest = new AtomicReference<>();
		FallbackPageFetcher fetcher = new FallbackPageFetcher(
			requestedUri -> new FetchedPage(requestedUri, finalUri, 429, "text/html", "<html>too many requests</html>"),
			requestedUri -> {
				fallbackRequest.set(requestedUri);
				return new FetchedPage(requestedUri, requestedUri, 200, "text/html", "<html>rendered</html>");
			}
		);

		FetchedPage page = fetcher.fetch(originalUri);

		assertThat(page.statusCode()).isEqualTo(200);
		assertThat(fallbackRequest.get()).isEqualTo(finalUri);
	}

	@Test
	void switchesRateLimitedSmartStoreProductToMobileHost() {
		URI originalUri = URI.create("https://smartstore.naver.com/sample/products/11933395125");
		AtomicReference<URI> fallbackRequest = new AtomicReference<>();
		FallbackPageFetcher fetcher = new FallbackPageFetcher(
			requestedUri -> new FetchedPage(requestedUri, requestedUri, 429, "text/html", "<html>too many requests</html>"),
			requestedUri -> {
				fallbackRequest.set(requestedUri);
				return new FetchedPage(requestedUri, requestedUri, 200, "text/html", "<html>rendered</html>");
			}
		);

		fetcher.fetch(originalUri);

		assertThat(fallbackRequest.get()).isEqualTo(
			URI.create("https://m.smartstore.naver.com/sample/products/11933395125")
		);
	}

	@Test
	void usesBrowserFallbackForAblyCloudflareChallengeShell() {
		URI uri = URI.create("https://m.a-bly.com/goods/70247267");
		AtomicReference<URI> fallbackRequest = new AtomicReference<>();
		FallbackPageFetcher fetcher = new FallbackPageFetcher(
			requestedUri -> new FetchedPage(
				requestedUri,
				requestedUri,
				200,
				"text/html",
				"<html><title>보안 확인 중..</title><body>에이블리에 연결하고 있습니다</body></html>"
			),
			requestedUri -> {
				fallbackRequest.set(requestedUri);
				return new FetchedPage(requestedUri, requestedUri, 200, "text/html", "<html>rendered product</html>");
			}
		);

		FetchedPage page = fetcher.fetch(uri);

		assertThat(fallbackRequest.get()).isEqualTo(uri);
		assertThat(page.body()).contains("rendered product");
	}
}

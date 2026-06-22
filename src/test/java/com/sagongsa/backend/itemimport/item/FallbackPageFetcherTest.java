package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
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
}

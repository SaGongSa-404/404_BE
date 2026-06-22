package com.sagongsa.backend.itemimport.item;

import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

public class FallbackPageFetcher implements PageFetcher, AutoCloseable {

	private final PageFetcher primary;
	private final PageFetcher fallback;

	public FallbackPageFetcher(PageFetcher primary, PageFetcher fallback) {
		this.primary = primary;
		this.fallback = fallback;
	}

	@Override
	public FetchedPage fetch(URI uri) {
		try {
			FetchedPage page = primary.fetch(uri);
			if (shouldFallback(page.statusCode())) {
				return fallback.fetch(uri);
			}
			return page;
		} catch (ResponseStatusException exception) {
			if (!shouldFallback(exception.getStatusCode())) {
				throw exception;
			}
			return fallback.fetch(uri);
		}
	}

	private boolean shouldFallback(int statusCode) {
		return statusCode == 403 || statusCode == 408 || statusCode == 429 || statusCode >= 500;
	}

	private boolean shouldFallback(HttpStatusCode statusCode) {
		return statusCode != null && shouldFallback(statusCode.value());
	}

	@Override
	public void close() throws Exception {
		if (fallback instanceof AutoCloseable closeable) {
			closeable.close();
		}
		if (primary instanceof AutoCloseable closeable) {
			closeable.close();
		}
	}
}

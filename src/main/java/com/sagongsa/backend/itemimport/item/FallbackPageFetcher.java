package com.sagongsa.backend.itemimport.item;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
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
			if (shouldFallback(page)) {
				return fallback.fetch(fallbackUri(uri, page));
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

	private boolean shouldFallback(FetchedPage page) {
		return shouldFallback(page.statusCode()) || isKnownErrorShell(page);
	}

	private URI fallbackUri(URI originalUri, FetchedPage page) {
		if (isKnownErrorShell(page) || isNaverProductUri(page.finalUri())) {
			return mobileNaverUri(page.finalUri());
		}
		return originalUri;
	}

	private boolean isNaverProductUri(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		String path = Optional.ofNullable(uri.getPath()).orElse("");
		return (host.equals("brand.naver.com") || host.equals("m.brand.naver.com"))
			&& path.contains("/products/");
	}

	private URI mobileNaverUri(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		if (!host.equals("brand.naver.com") && !host.equals("m.brand.naver.com")) {
			return uri;
		}
		try {
			return new URI(uri.getScheme(), uri.getUserInfo(), "m.brand.naver.com", uri.getPort(), uri.getPath(), uri.getRawQuery(), uri.getFragment());
		} catch (URISyntaxException exception) {
			return uri;
		}
	}

	private boolean isKnownErrorShell(FetchedPage page) {
		String host = Optional.ofNullable(page.finalUri().getHost()).orElse("").toLowerCase(Locale.ROOT);
		String body = Optional.ofNullable(page.body()).orElse("");
		return host.equals("brand.naver.com")
			&& (body.contains("시스템오류") || body.contains("에러페이지"));
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

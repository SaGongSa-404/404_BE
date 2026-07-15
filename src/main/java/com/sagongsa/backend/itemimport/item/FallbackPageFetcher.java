package com.sagongsa.backend.itemimport.item;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
		Optional<URI> naverLoginTarget = naverLoginTarget(page.finalUri());
		if (naverLoginTarget.isPresent()) {
			return mobileNaverUri(naverLoginTarget.get());
		}
		if (isKnownErrorShell(page) || isNaverProductUri(page.finalUri())) {
			return mobileNaverUri(page.finalUri());
		}
		return originalUri;
	}

	private Optional<URI> naverLoginTarget(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		String path = Optional.ofNullable(uri.getPath()).orElse("");
		String rawQuery = uri.getRawQuery();
		if (!host.equals("nid.naver.com") || !path.endsWith("/nidlogin.login") || rawQuery == null) {
			return Optional.empty();
		}
		for (String parameter : rawQuery.split("&")) {
			int separator = parameter.indexOf('=');
			if (separator <= 0 || !parameter.substring(0, separator).equals("url")) {
				continue;
			}
			try {
				URI target = URI.create(URLDecoder.decode(parameter.substring(separator + 1), StandardCharsets.UTF_8));
				return isNaverProductUri(target) ? Optional.of(target) : Optional.empty();
			} catch (IllegalArgumentException exception) {
				return Optional.empty();
			}
		}
		return Optional.empty();
	}

	private boolean isNaverProductUri(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		String path = Optional.ofNullable(uri.getPath()).orElse("");
		return (host.equals("brand.naver.com") || host.equals("m.brand.naver.com")
			|| host.equals("smartstore.naver.com") || host.equals("m.smartstore.naver.com"))
			&& path.contains("/products/");
	}

	private URI mobileNaverUri(URI uri) {
		String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
		if (!host.equals("brand.naver.com") && !host.equals("m.brand.naver.com")
			&& !host.equals("smartstore.naver.com") && !host.equals("m.smartstore.naver.com")) {
			return uri;
		}
		try {
			String mobileHost = host.contains("smartstore") ? "m.smartstore.naver.com" : "m.brand.naver.com";
			return new URI(uri.getScheme(), uri.getUserInfo(), mobileHost, uri.getPort(), uri.getPath(), uri.getRawQuery(), uri.getFragment());
		} catch (URISyntaxException exception) {
			return uri;
		}
	}

	private boolean isKnownErrorShell(FetchedPage page) {
		String host = Optional.ofNullable(page.finalUri().getHost()).orElse("").toLowerCase(Locale.ROOT);
		String body = Optional.ofNullable(page.body()).orElse("");
		boolean naverLoginShell = naverLoginTarget(page.finalUri()).isPresent();
		boolean naverErrorShell = (host.equals("brand.naver.com") || host.equals("smartstore.naver.com"))
			&& (body.contains("시스템오류") || body.contains("에러페이지"));
		boolean ablyChallengeShell = host.equals("m.a-bly.com")
			&& (body.contains("보안 확인 중")
				|| body.contains("에이블리에 연결하고 있습니다")
				|| body.contains("challenge-error-text")
				|| body.contains("/cdn-cgi/challenge-platform/")
				|| body.contains("Enable JavaScript and cookies to continue"));
		return naverLoginShell || naverErrorShell || ablyChallengeShell;
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

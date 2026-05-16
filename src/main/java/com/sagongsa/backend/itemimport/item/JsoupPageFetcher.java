package com.sagongsa.backend.itemimport.item;

import java.io.IOException;
import java.net.URI;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class JsoupPageFetcher implements PageFetcher {

	private static final String ANDROID_USER_AGENT =
		"Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36";
	private static final int MAX_REDIRECTS = 5;

	@Override
	public FetchedPage fetch(URI uri) {
		URI currentUri = uri;
		try {
			for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
				ShoppingUrlSafety.validatePublicHost(currentUri);
				Connection.Response response = Jsoup.connect(currentUri.toString())
					.userAgent(ANDROID_USER_AGENT)
					.header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
					.followRedirects(false)
					.ignoreContentType(true)
					.ignoreHttpErrors(true)
					.timeout(10_000)
					.execute();

				if (isRedirect(response.statusCode())) {
					String location = response.header("Location");
					if (location != null && !location.isBlank()) {
						currentUri = currentUri.resolve(location);
						continue;
					}
				}

				return new FetchedPage(
					uri,
					URI.create(response.url().toString()),
					response.statusCode(),
					response.contentType(),
					response.body()
				);
			}
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Shopping page redirected too many times");
		} catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch shopping page", exception);
		}
	}

	private boolean isRedirect(int statusCode) {
		return statusCode >= 300 && statusCode < 400;
	}
}

package com.sagongsa.backend.auth;

import com.sagongsa.backend.config.AppAuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ReviewerTokenRateLimiter {

	private final AppAuthProperties authProperties;
	private final Clock clock;
	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

	@Autowired
	public ReviewerTokenRateLimiter(AppAuthProperties authProperties) {
		this(authProperties, Clock.systemUTC());
	}

	ReviewerTokenRateLimiter(AppAuthProperties authProperties, Clock clock) {
		this.authProperties = authProperties;
		this.clock = clock;
	}

	public void assertAllowed(String key) {
		AppAuthProperties.ReviewerToken properties = authProperties.getReviewerToken();
		int maxAttempts = properties.getRateLimitMaxAttempts();
		if (maxAttempts <= 0) {
			return;
		}

		Instant now = Instant.now(clock);
		Duration windowSize = properties.getRateLimitWindow();
		if (windowSize == null || windowSize.isNegative() || windowSize.isZero()) {
			windowSize = Duration.ofMinutes(10);
		}
		Duration effectiveWindowSize = windowSize;

		Window window = windows.compute(key, (ignored, current) -> {
			if (current == null || !now.isBefore(current.expiresAt())) {
				return new Window(now.plus(effectiveWindowSize), 1);
			}
			return new Window(current.expiresAt(), current.count() + 1);
		});

		if (window.count() > maxAttempts) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "reviewer token request rate limit exceeded");
		}
	}

	private record Window(Instant expiresAt, int count) {
	}
}

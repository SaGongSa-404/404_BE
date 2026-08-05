package com.sagongsa.backend.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class FixedWindowRateLimiter {

	private static final long CLEANUP_INTERVAL = 256;

	private final Clock clock;
	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
	private final AtomicLong requests = new AtomicLong();

	FixedWindowRateLimiter(Clock clock) {
		this.clock = clock;
	}

	Decision acquire(String key, int maxRequests, Duration windowSize, int maxTrackedKeys) {
		if (maxRequests <= 0 || windowSize == null || windowSize.isZero() || windowSize.isNegative()) {
			return Decision.notLimited();
		}

		Instant now = Instant.now(clock);
		if (requests.incrementAndGet() % CLEANUP_INTERVAL == 0) {
			removeExpired(now);
		}

		Window existing = windows.get(key);
		if (existing == null && maxTrackedKeys > 0 && windows.size() >= maxTrackedKeys) {
			removeExpired(now);
			if (windows.size() >= maxTrackedKeys) {
				return Decision.untrackedDecision();
			}
		}

		Window updated = windows.compute(key, (ignored, current) -> {
			if (current == null || !now.isBefore(current.expiresAt())) {
				return new Window(now.plus(windowSize), 1);
			}
			return new Window(current.expiresAt(), current.count() + 1);
		});

		int warningAt = Math.max(1, (int)Math.ceil(maxRequests * 0.8d));
		boolean allowed = updated.count() <= maxRequests;
		return new Decision(
			allowed,
			updated.count(),
			Math.max(0, maxRequests - updated.count()),
			updated.expiresAt(),
			updated.count() == warningAt,
			updated.count() == maxRequests + 1,
			false
		);
	}

	private void removeExpired(Instant now) {
		windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
	}

	record Decision(
		boolean allowed,
		int count,
		int remaining,
		Instant resetsAt,
		boolean warningReached,
		boolean firstRejection,
		boolean untracked
	) {
		static Decision notLimited() {
			return new Decision(true, 0, Integer.MAX_VALUE, Instant.EPOCH, false, false, false);
		}

		static Decision untrackedDecision() {
			return new Decision(true, 0, Integer.MAX_VALUE, Instant.EPOCH, false, false, true);
		}
	}

	private record Window(Instant expiresAt, int count) {
	}
}

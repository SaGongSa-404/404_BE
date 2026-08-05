package com.sagongsa.backend.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-08-06T00:00:00Z"));
	private final FixedWindowRateLimiter rateLimiter = new FixedWindowRateLimiter(clock);

	@Test
	void allowsLimitAndRejectsNextRequest() {
		assertThat(acquire("api:203.0.113.10", 2).allowed()).isTrue();
		assertThat(acquire("api:203.0.113.10", 2).allowed()).isTrue();

		FixedWindowRateLimiter.Decision rejected = acquire("api:203.0.113.10", 2);

		assertThat(rejected.allowed()).isFalse();
		assertThat(rejected.firstRejection()).isTrue();
		assertThat(rejected.remaining()).isZero();
	}

	@Test
	void keepsClientWindowsIndependent() {
		acquire("api:203.0.113.10", 1);

		assertThat(acquire("api:198.51.100.20", 1).allowed()).isTrue();
	}

	@Test
	void resetsAfterWindowExpires() {
		acquire("api:203.0.113.10", 1);
		assertThat(acquire("api:203.0.113.10", 1).allowed()).isFalse();

		clock.advance(Duration.ofMinutes(1));

		assertThat(acquire("api:203.0.113.10", 1).allowed()).isTrue();
	}

	@Test
	void failsOpenWhenKeyCapacityIsFull() {
		rateLimiter.acquire("api:203.0.113.10", 1, Duration.ofMinutes(1), 1);

		FixedWindowRateLimiter.Decision decision =
			rateLimiter.acquire("api:198.51.100.20", 1, Duration.ofMinutes(1), 1);

		assertThat(decision.allowed()).isTrue();
		assertThat(decision.untracked()).isTrue();
	}

	private FixedWindowRateLimiter.Decision acquire(String key, int maxRequests) {
		return rateLimiter.acquire(key, maxRequests, Duration.ofMinutes(1), 100);
	}

	private static class MutableClock extends Clock {

		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}

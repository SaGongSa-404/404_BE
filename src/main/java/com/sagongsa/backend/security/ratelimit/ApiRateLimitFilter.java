package com.sagongsa.backend.security.ratelimit;

import com.sagongsa.backend.config.ApiRateLimitProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);
	private static final Duration OVERFLOW_WARNING_INTERVAL = Duration.ofMinutes(1);
	private static final String IMPORT_LINK_PATH = "/api/v1/items/import-link";
	private static final String IMPORT_JOBS_PATH = "/api/v1/items/import-jobs";

	private final ApiRateLimitProperties properties;
	private final ClientIpResolver clientIpResolver;
	private final FixedWindowRateLimiter rateLimiter;
	private final MeterRegistry meterRegistry;
	private final Clock clock;
	private final Map<String, Counter> rejectedCounters = new ConcurrentHashMap<>();
	private final AtomicLong nextOverflowWarningAtMillis = new AtomicLong();

	@Autowired
	public ApiRateLimitFilter(
		ApiRateLimitProperties properties,
		ClientIpResolver clientIpResolver,
		MeterRegistry meterRegistry
	) {
		this(properties, clientIpResolver, meterRegistry, Clock.systemUTC());
	}

	ApiRateLimitFilter(
		ApiRateLimitProperties properties,
		ClientIpResolver clientIpResolver,
		MeterRegistry meterRegistry,
		Clock clock
	) {
		this.properties = properties;
		this.clientIpResolver = clientIpResolver;
		this.meterRegistry = meterRegistry;
		this.clock = clock;
		this.rateLimiter = new FixedWindowRateLimiter(clock);
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (!properties.isEnabled()) {
			filterChain.doFilter(request, response);
			return;
		}
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			filterChain.doFilter(request, response);
			return;
		}

		ClientIpResolver.ClientIp clientIp = clientIpResolver.resolve(request);
		if (clientIp.internal()) {
			filterChain.doFilter(request, response);
			return;
		}

		for (Policy policy : policiesFor(request)) {
			FixedWindowRateLimiter.Decision decision = rateLimiter.acquire(
				policy.name().toLowerCase(Locale.ROOT) + ":" + clientIp.address(),
				policy.properties().getMaxRequests(),
				policy.properties().getWindow(),
				properties.getMaxTrackedKeys()
			);

			if (decision.untracked()) {
				logOverflowOnce();
				continue;
			}
			if (decision.warningReached()) {
				log.warn(
					"api rate limit nearing threshold policy={} clientIp={} method={} path={} count={} limit={} windowSeconds={}",
					policy.name(),
					clientIp.address(),
					request.getMethod(),
					request.getRequestURI(),
					decision.count(),
					policy.properties().getMaxRequests(),
					windowSeconds(policy.properties().getWindow())
				);
			}
			if (!decision.allowed()) {
				rejectedCounter(policy).increment();
				if (decision.firstRejection()) {
					log.warn(
						"api rate limit exceeded policy={} clientIp={} method={} path={} count={} limit={} resetAt={}",
						policy.name(),
						clientIp.address(),
						request.getMethod(),
						request.getRequestURI(),
						decision.count(),
						policy.properties().getMaxRequests(),
						decision.resetsAt()
					);
				}
				writeRateLimitedResponse(response, policy, decision);
				return;
			}
		}

		filterChain.doFilter(request, response);
	}

	private List<Policy> policiesFor(HttpServletRequest request) {
		String path = request.getRequestURI();
		List<Policy> policies = new ArrayList<>(3);

		if ("/health".equals(path) || "/api/health".equals(path)) {
			policies.add(new Policy("HEALTH", properties.getHealth()));
		}
		if (path.startsWith("/api/")) {
			policies.add(new Policy("API", properties.getApi()));
		}
		if (isAuthenticationPath(path)) {
			policies.add(new Policy("AUTHENTICATION", properties.getAuthentication()));
		}
		if ("POST".equalsIgnoreCase(request.getMethod())
			&& (IMPORT_LINK_PATH.equals(path) || IMPORT_JOBS_PATH.equals(path))) {
			policies.add(new Policy("EXPENSIVE_REQUEST", properties.getExpensiveRequest()));
		}

		return policies;
	}

	private boolean isAuthenticationPath(String path) {
		return path.startsWith("/oauth2/")
			|| path.startsWith("/login/oauth2/")
			|| path.startsWith("/api/auth/");
	}

	private Counter rejectedCounter(Policy policy) {
		return rejectedCounters.computeIfAbsent(policy.name(), ignored -> Counter.builder(
				"app.security.rate_limit.rejected"
			)
			.description("Requests rejected by the application rate limiter")
			.tag("policy", policy.name().toLowerCase(Locale.ROOT))
			.register(meterRegistry));
	}

	private void writeRateLimitedResponse(
		HttpServletResponse response,
		Policy policy,
		FixedWindowRateLimiter.Decision decision
	) throws IOException {
		long retryAfterSeconds = Math.max(
			1,
			(long)Math.ceil(
				Duration.between(Instant.now(clock), decision.resetsAt()).toMillis() / 1000.0d
			)
		);
		response.setStatus(429);
		response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
		response.setHeader("X-RateLimit-Limit", Integer.toString(policy.properties().getMaxRequests()));
		response.setHeader("X-RateLimit-Remaining", Integer.toString(decision.remaining()));
		response.setHeader("X-RateLimit-Reset", Long.toString(decision.resetsAt().getEpochSecond()));
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().printf(
			"{\"status\":429,\"error\":\"Too Many Requests\",\"policy\":\"%s\",\"retryAfterSeconds\":%d}",
			policy.name().toLowerCase(Locale.ROOT),
			retryAfterSeconds
		);
	}

	private void logOverflowOnce() {
		long now = clock.millis();
		long next = nextOverflowWarningAtMillis.get();
		if (now < next) {
			return;
		}
		long updated = now + OVERFLOW_WARNING_INTERVAL.toMillis();
		if (nextOverflowWarningAtMillis.compareAndSet(next, updated)) {
			log.warn(
				"api rate limiter key capacity reached maxTrackedKeys={} new keys are temporarily fail-open",
				properties.getMaxTrackedKeys()
			);
		}
	}

	private long windowSeconds(Duration window) {
		return window == null ? 0 : Math.max(1, window.toSeconds());
	}

	private record Policy(String name, ApiRateLimitProperties.Policy properties) {
	}
}

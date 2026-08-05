package com.sagongsa.backend.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.config.ApiRateLimitProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRateLimitFilterTest {

	private ApiRateLimitProperties properties;
	private SimpleMeterRegistry meterRegistry;
	private ApiRateLimitFilter filter;

	@BeforeEach
	void setUp() {
		properties = new ApiRateLimitProperties();
		properties.getApi().setMaxRequests(3);
		properties.getAuthentication().setMaxRequests(2);
		properties.getExpensiveRequest().setMaxRequests(1);
		properties.getHealth().setMaxRequests(2);
		meterRegistry = new SimpleMeterRegistry();
		filter = new ApiRateLimitFilter(
			properties,
			new ClientIpResolver(),
			meterRegistry,
			Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC)
		);
	}

	@Test
	void rejectsAuthenticationBurstWithRetryHeaders() throws Exception {
		assertThat(execute("GET", "/oauth2/authorization/google", "203.0.113.10").getStatus()).isEqualTo(200);
		assertThat(execute("GET", "/oauth2/authorization/google", "203.0.113.10").getStatus()).isEqualTo(200);

		MockHttpServletResponse rejected =
			execute("GET", "/oauth2/authorization/google", "203.0.113.10");

		assertThat(rejected.getStatus()).isEqualTo(429);
		assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
		assertThat(rejected.getHeader("X-RateLimit-Limit")).isEqualTo("2");
		assertThat(rejected.getContentAsString()).contains("\"policy\":\"authentication\"");
		assertThat(meterRegistry.get("app.security.rate_limit.rejected")
			.tag("policy", "authentication")
			.counter()
			.count()).isEqualTo(1);
	}

	@Test
	void appliesStricterLimitToExpensiveImportRequests() throws Exception {
		assertThat(execute("POST", "/api/v1/items/import-link", "203.0.113.11").getStatus()).isEqualTo(200);

		MockHttpServletResponse rejected =
			execute("POST", "/api/v1/items/import-link", "203.0.113.11");

		assertThat(rejected.getStatus()).isEqualTo(429);
		assertThat(rejected.getContentAsString()).contains("\"policy\":\"expensive_request\"");
	}

	@Test
	void keepsDifferentClientAddressesIndependent() throws Exception {
		execute("GET", "/api/v1/users/me", "203.0.113.12");
		execute("GET", "/api/v1/users/me", "203.0.113.12");
		execute("GET", "/api/v1/users/me", "203.0.113.12");

		assertThat(execute("GET", "/api/v1/users/me", "198.51.100.12").getStatus()).isEqualTo(200);
	}

	@Test
	void bypassesInternalLoopbackTrafficWithoutForwardedAddress() throws Exception {
		for (int i = 0; i < 10; i++) {
			MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
			request.setRemoteAddr("127.0.0.1");
			MockHttpServletResponse response = new MockHttpServletResponse();

			filter.doFilter(request, response, new MockFilterChain());

			assertThat(response.getStatus()).isEqualTo(200);
		}
	}

	@Test
	void bypassesCorsPreflightRequests() throws Exception {
		for (int i = 0; i < 10; i++) {
			assertThat(execute("OPTIONS", "/api/auth/me", "203.0.113.13").getStatus()).isEqualTo(200);
		}
	}

	private MockHttpServletResponse execute(String method, String path, String clientIp) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("X-Forwarded-For", clientIp);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		return response;
	}
}

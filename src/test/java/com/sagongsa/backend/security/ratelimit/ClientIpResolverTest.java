package com.sagongsa.backend.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

	private final ClientIpResolver resolver = new ClientIpResolver();

	@Test
	void trustsForwardedAddressOnlyFromLoopbackProxy() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("X-Forwarded-For", "203.0.113.10");

		ClientIpResolver.ClientIp clientIp = resolver.resolve(request);

		assertThat(clientIp.address()).isEqualTo("203.0.113.10");
		assertThat(clientIp.internal()).isFalse();
	}

	@Test
	void ignoresSpoofedForwardedAddressOnDirectConnection() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("198.51.100.20");
		request.addHeader("X-Forwarded-For", "203.0.113.99");

		ClientIpResolver.ClientIp clientIp = resolver.resolve(request);

		assertThat(clientIp.address()).isEqualTo("198.51.100.20");
		assertThat(clientIp.internal()).isFalse();
	}

	@Test
	void marksLoopbackWithoutForwardedAddressAsInternal() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("::1");

		ClientIpResolver.ClientIp clientIp = resolver.resolve(request);

		assertThat(clientIp.internal()).isTrue();
	}

	@Test
	void rejectsNonLiteralForwardedAddress() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("X-Forwarded-For", "attacker.example");

		ClientIpResolver.ClientIp clientIp = resolver.resolve(request);

		assertThat(clientIp.internal()).isTrue();
	}
}

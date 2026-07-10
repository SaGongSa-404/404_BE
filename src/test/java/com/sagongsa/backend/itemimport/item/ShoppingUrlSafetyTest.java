package com.sagongsa.backend.itemimport.item;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ShoppingUrlSafetyTest {

	@Test
	void rejectsLoopbackHost() {
		assertThatThrownBy(() -> ShoppingUrlSafety.validatePublicHost(URI.create("http://localhost/items/1")))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("Private network");
	}

	@Test
	void rejectsIpv6UniqueLocalAddress() {
		assertThatThrownBy(() -> ShoppingUrlSafety.validatePublicHost(URI.create("http://[fc00::1]/items/1")))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("Private network");
	}

	@Test
	void acceptsPublicLiteralAddress() {
		assertThatCode(() -> ShoppingUrlSafety.validatePublicHost(URI.create("http://[2606:4700:4700::1111]/items/1")))
			.doesNotThrowAnyException();
	}
}

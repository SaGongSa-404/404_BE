package com.sagongsa.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StaticAppSecurityTest {

	@Test
	void feedImageUrlIsAssignedThroughAProtocolCheckedDomProperty() throws IOException {
		String html = readAppHtml();

		assertThat(html)
			.doesNotContain("src=\"${p.imageUrl}\"")
			.contains("const imageUrl = toSafeImageUrl(p.imageUrl);")
			.contains("url.protocol === 'http:' || url.protocol === 'https:'")
			.contains("img.src = imageUrl;");
	}

	@Test
	void profileTextIsEscapedBeforeInnerHtmlRendering() throws IOException {
		assertThat(readAppHtml())
			.contains("${esc(me.nickname || '-')}")
			.contains("${esc(me.mascotName || '-')}")
			.contains("${esc(me.status)}");
	}

	private String readAppHtml() throws IOException {
		try (var input = getClass().getResourceAsStream("/static/app.html")) {
			assertThat(input).isNotNull();
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}

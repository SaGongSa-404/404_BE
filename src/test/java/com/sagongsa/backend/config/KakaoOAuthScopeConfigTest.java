package com.sagongsa.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

class KakaoOAuthScopeConfigTest {

	private static final String KAKAO_SCOPE_PREFIX = "spring.security.oauth2.client.registration.kakao.scope[";

	@Test
	void kakaoLoginDoesNotRequestDisabledProfileImageConsentItem() {
		assertKakaoScopes("src/main/resources/application.yml");
		assertKakaoScopes("src/test/resources/application.yml");
	}

	private static void assertKakaoScopes(String path) {
		Properties properties = loadProperties(path);
		List<String> scopes = properties.stringPropertyNames().stream()
			.filter(name -> name.startsWith(KAKAO_SCOPE_PREFIX))
			.sorted()
			.map(properties::getProperty)
			.toList();

		assertThat(scopes)
			.as(path)
			.containsExactly("profile_nickname")
			.doesNotContain("profile_image");
	}

	private static Properties loadProperties(String path) {
		YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
		factoryBean.setResources(new FileSystemResource(path));

		Properties properties = factoryBean.getObject();
		assertThat(properties).as(path).isNotNull();
		return properties;
	}
}

package com.sagongsa.backend.auth;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CurrentUserWebMvcConfig implements WebMvcConfigurer {

	private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;

	public CurrentUserWebMvcConfig(CurrentUserIdArgumentResolver currentUserIdArgumentResolver) {
		this.currentUserIdArgumentResolver = currentUserIdArgumentResolver;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(currentUserIdArgumentResolver);
	}
}

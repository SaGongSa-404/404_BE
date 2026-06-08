package com.sagongsa.backend.observability;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class RequestTraceWebMvcConfig implements WebMvcConfigurer {

	private final RequestTraceInterceptor requestTraceInterceptor;

	RequestTraceWebMvcConfig(RequestTraceInterceptor requestTraceInterceptor) {
		this.requestTraceInterceptor = requestTraceInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(requestTraceInterceptor);
	}
}

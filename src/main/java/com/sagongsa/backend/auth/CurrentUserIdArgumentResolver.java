package com.sagongsa.backend.auth;

import java.security.Principal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

@Component
class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

	private final boolean trustedHeaderEnabled;
	private final String trustedHeaderName;

	CurrentUserIdArgumentResolver(
		@Value("${app.auth.trusted-user-id-header.enabled:false}") boolean trustedHeaderEnabled,
		@Value("${app.auth.trusted-user-id-header.name:X-User-Id}") String trustedHeaderName
	) {
		this.trustedHeaderEnabled = trustedHeaderEnabled;
		this.trustedHeaderName = trustedHeaderName;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentUserId.class)
			&& UUID.class.equals(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(
		MethodParameter parameter,
		ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest,
		WebDataBinderFactory binderFactory
	) {
		String rawUserId = webRequest.getHeader(trustedHeaderName);
		if (trustedHeaderEnabled && StringUtils.hasText(rawUserId)) {
			return parseUserId(rawUserId, trustedHeaderName + " header");
		}

		Principal principal = webRequest.getUserPrincipal();
		if (principal != null && StringUtils.hasText(principal.getName())) {
			return parseUserId(principal.getName(), "authenticated principal");
		}

		if (trustedHeaderEnabled) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, trustedHeaderName + " header is required.");
		}

		throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required.");
	}

	private UUID parseUserId(String rawUserId, String source) {
		try {
			return UUID.fromString(rawUserId.trim());
		}
		catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, source + " must be a UUID.");
		}
	}
}

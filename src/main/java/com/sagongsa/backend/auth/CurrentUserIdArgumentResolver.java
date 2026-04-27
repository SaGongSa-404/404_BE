package com.sagongsa.backend.auth;

import java.util.UUID;
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
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

	private static final String USER_ID_HEADER = "X-User-Id";

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
		String rawUserId = webRequest.getHeader(USER_ID_HEADER);
		if (!StringUtils.hasText(rawUserId)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, USER_ID_HEADER + " header is required.");
		}
		try {
			return UUID.fromString(rawUserId.trim());
		}
		catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, USER_ID_HEADER + " header must be a UUID.");
		}
	}
}

package com.sagongsa.backend.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
class RequestTraceInterceptor implements HandlerInterceptor {

	private final String trustedHeaderName;
	private final boolean trustedHeaderEnabled;

	RequestTraceInterceptor(
		@Value("${app.auth.trusted-user-id-header.name:X-User-Id}") String trustedHeaderName,
		@Value("${app.auth.trusted-user-id-header.enabled:false}") boolean trustedHeaderEnabled,
		Environment environment
	) {
		this.trustedHeaderName = trustedHeaderName;
		this.trustedHeaderEnabled = trustedHeaderEnabled || !environment.acceptsProfiles(Profiles.of("prod"));
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String userId = resolveAuthenticatedUserId(request.getUserPrincipal());
		if (!StringUtils.hasText(userId) && trustedHeaderEnabled) {
			userId = resolveTrustedHeaderUserId(request.getHeader(trustedHeaderName));
		}
		if (StringUtils.hasText(userId)) {
			MDC.put(RequestTraceFilter.MDC_USER_ID_KEY, userId);
		}
		return true;
	}

	private String resolveAuthenticatedUserId(Principal principal) {
		if (principal instanceof Authentication authentication && authentication.getPrincipal() instanceof Jwt jwt) {
			String claimUserId = jwt.getClaimAsString("userId");
			if (isUuid(claimUserId)) {
				return claimUserId.trim();
			}
			String subject = jwt.getSubject();
			if (isUuid(subject)) {
				return subject.trim();
			}
		}
		if (principal != null && isUuid(principal.getName())) {
			return principal.getName().trim();
		}
		return null;
	}

	private String resolveTrustedHeaderUserId(String rawUserId) {
		return isUuid(rawUserId) ? rawUserId.trim() : null;
	}

	private boolean isUuid(String value) {
		if (!StringUtils.hasText(value)) {
			return false;
		}
		try {
			UUID.fromString(value.trim());
			return true;
		}
		catch (IllegalArgumentException exception) {
			return false;
		}
	}
}

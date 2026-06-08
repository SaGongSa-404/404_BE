package com.sagongsa.backend.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

	public static final String REQUEST_ID_HEADER = "X-Request-Id";
	static final String MDC_REQUEST_ID_KEY = "requestId";
	static final String MDC_USER_ID_KEY = "userId";

	private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);
	private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		long startedAt = System.nanoTime();
		String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
		MDC.put(MDC_REQUEST_ID_KEY, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);

		try {
			filterChain.doFilter(request, response);
		}
		finally {
			logCompletedRequest(request, response, startedAt);
			MDC.remove(MDC_USER_ID_KEY);
			MDC.remove(MDC_REQUEST_ID_KEY);
		}
	}

	private String resolveRequestId(String rawRequestId) {
		if (StringUtils.hasText(rawRequestId)) {
			String trimmed = rawRequestId.trim();
			if (SAFE_REQUEST_ID.matcher(trimmed).matches()) {
				return trimmed;
			}
		}
		return UUID.randomUUID().toString();
	}

	private void logCompletedRequest(
		HttpServletRequest request,
		HttpServletResponse response,
		long startedAt
	) {
		long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
		int status = response.getStatus();
		if (status >= 500) {
			log.warn("request completed method={} path={} status={} durationMs={}",
				request.getMethod(), request.getRequestURI(), status, durationMs);
			return;
		}
		log.info("request completed method={} path={} status={} durationMs={}",
			request.getMethod(), request.getRequestURI(), status, durationMs);
	}
}

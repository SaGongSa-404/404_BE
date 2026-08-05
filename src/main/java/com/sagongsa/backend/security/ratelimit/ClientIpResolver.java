package com.sagongsa.backend.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ClientIpResolver {

	private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
	private static final int MAX_IP_LITERAL_LENGTH = 45;

	public ClientIp resolve(HttpServletRequest request) {
		String remoteAddress = normalizeLiteral(request.getRemoteAddr());
		boolean loopback = isLoopback(remoteAddress);

		if (loopback) {
			String forwardedAddress = firstForwardedAddress(request.getHeader(FORWARDED_FOR_HEADER));
			if (forwardedAddress != null && !isLoopback(forwardedAddress)) {
				return new ClientIp(forwardedAddress, false);
			}
			return new ClientIp(remoteAddress, true);
		}

		return new ClientIp(remoteAddress, false);
	}

	private String firstForwardedAddress(String header) {
		if (!StringUtils.hasText(header)) {
			return null;
		}
		String normalized = normalizeLiteral(header.split(",", 2)[0].trim());
		return "unknown".equals(normalized) ? null : normalized;
	}

	private String normalizeLiteral(String rawAddress) {
		if (!StringUtils.hasText(rawAddress)) {
			return "unknown";
		}

		String candidate = rawAddress.trim();
		if (candidate.startsWith("[") && candidate.endsWith("]")) {
			candidate = candidate.substring(1, candidate.length() - 1);
		}
		if (candidate.length() > MAX_IP_LITERAL_LENGTH || !candidate.matches("[0-9A-Fa-f:.]+")) {
			return "unknown";
		}

		try {
			return InetAddress.getByName(candidate).getHostAddress();
		} catch (UnknownHostException exception) {
			return "unknown";
		}
	}

	private boolean isLoopback(String address) {
		if ("unknown".equals(address)) {
			return false;
		}
		try {
			return InetAddress.getByName(address).isLoopbackAddress();
		} catch (UnknownHostException exception) {
			return false;
		}
	}

	public record ClientIp(String address, boolean internal) {
	}
}

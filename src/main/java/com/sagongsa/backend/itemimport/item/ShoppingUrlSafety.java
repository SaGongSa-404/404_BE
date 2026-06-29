package com.sagongsa.backend.itemimport.item;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ShoppingUrlSafety {

	private ShoppingUrlSafety() {
	}

	static void validatePublicHost(URI uri) {
		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shopping url host is required");
		}

		try {
			for (InetAddress address : InetAddress.getAllByName(host)) {
				if (address.isAnyLocalAddress()
					|| address.isLoopbackAddress()
					|| address.isLinkLocalAddress()
					|| address.isSiteLocalAddress()
					|| isUniqueLocalAddress(address)
					|| address.isMulticastAddress()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Private network shopping urls are not supported");
				}
			}
		} catch (UnknownHostException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shopping url host could not be resolved", exception);
		}
	}

	private static boolean isUniqueLocalAddress(InetAddress address) {
		if (!(address instanceof Inet6Address)) {
			return false;
		}
		byte[] bytes = address.getAddress();
		return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
	}
}

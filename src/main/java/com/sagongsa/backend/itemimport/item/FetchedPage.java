package com.sagongsa.backend.itemimport.item;

import java.net.URI;

public record FetchedPage(
	URI requestedUri,
	URI finalUri,
	int statusCode,
	String contentType,
	String body
) {
}

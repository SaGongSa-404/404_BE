package com.sagongsa.backend.itemimport.item;

import java.net.URI;

public interface PageFetcher {

	FetchedPage fetch(URI uri);
}

package com.sagongsa.backend.social;

import java.time.Instant;
import java.util.List;

record PostListResponse(
	List<PostResponse> posts,
	Instant nextCursor,
	boolean hasMore
) {}

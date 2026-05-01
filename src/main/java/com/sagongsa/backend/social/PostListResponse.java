package com.sagongsa.backend.social;

import java.util.List;
import java.util.UUID;

public record PostListResponse(
	List<PostResponse> posts,
	UUID nextCursor,
	boolean hasMore
) {}

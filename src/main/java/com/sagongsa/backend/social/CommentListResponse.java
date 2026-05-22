package com.sagongsa.backend.social;

import java.util.List;

record CommentListResponse(
	List<CommentResponse> comments,
	long total
) {}

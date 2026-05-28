package com.sagongsa.backend.social;

import jakarta.validation.constraints.Size;

record UpdatePostRequest(
	@Size(max = 500, message = "본문은 최대 500자까지 가능합니다.")
	String body
) {}

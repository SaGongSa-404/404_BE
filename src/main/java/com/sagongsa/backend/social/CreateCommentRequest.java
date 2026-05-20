package com.sagongsa.backend.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CreateCommentRequest(
	@NotBlank(message = "댓글 내용은 필수입니다.")
	@Size(max = 300, message = "댓글은 최대 300자까지 가능합니다.")
	String body
) {}

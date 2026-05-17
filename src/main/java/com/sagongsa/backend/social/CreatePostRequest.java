package com.sagongsa.backend.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record CreatePostRequest(
	@NotBlank(message = "제목은 필수입니다.")
	@Size(max = 140, message = "제목은 최대 140자까지 가능합니다.")
	String title,

	@Size(max = 500, message = "본문은 최대 500자까지 가능합니다.")
	String body,

	String imageUrl,

	Integer price,

	UUID itemId
) {}

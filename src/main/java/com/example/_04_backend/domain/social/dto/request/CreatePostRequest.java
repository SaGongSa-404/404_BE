package com.example._04_backend.domain.social.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreatePostRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 140, message = "제목은 최대 140자까지 가능합니다.")
    private String title;

    @Size(max = 500, message = "본문은 최대 500자까지 가능합니다.")
    private String body;

    private String imageUrl;

    private Integer price;
}

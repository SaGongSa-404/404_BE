package com.example._04_backend.domain.social.dto.request;

import com.example._04_backend.global.common.enums.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CreatePostRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 100, message = "제목은 최대 100자까지 가능합니다.")
    private String title;

    @Size(max = 500, message = "본문은 최대 500자까지 가능합니다.")
    private String body;

    private String productUrl;

    private String imageUrl;

    private Integer price;

    @NotNull(message = "카테고리는 필수입니다.")
    private Category category;

    private UUID wishId;
}

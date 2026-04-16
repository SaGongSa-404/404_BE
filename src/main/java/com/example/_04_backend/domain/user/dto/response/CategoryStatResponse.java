package com.example._04_backend.domain.user.dto.response;

import com.example._04_backend.global.common.enums.Category;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CategoryStatResponse {
    private Category category;
    private Integer spent;
    private Long count;
}

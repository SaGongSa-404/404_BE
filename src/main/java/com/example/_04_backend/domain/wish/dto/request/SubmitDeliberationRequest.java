package com.example._04_backend.domain.wish.dto.request;

import com.example._04_backend.domain.wish.enums.ItemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class SubmitDeliberationRequest {

    @NotNull(message = "answers는 필수입니다.")
    @Size(min = 4, max = 4, message = "answers는 4개여야 합니다.")
    private List<Boolean> answers;

    @NotNull(message = "decision은 필수입니다.")
    private ItemStatus decision;
}

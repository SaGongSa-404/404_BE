package com.example._04_backend.domain.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateProfileRequest {

    @Size(min = 1, max = 10, message = "닉네임은 1~10자여야 합니다.")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", message = "닉네임은 한글, 영문, 숫자만 허용됩니다.")
    private String nickname;

    @Size(min = 1, max = 10, message = "너구리 이름은 1~10자여야 합니다.")
    private String raccoonName;
}

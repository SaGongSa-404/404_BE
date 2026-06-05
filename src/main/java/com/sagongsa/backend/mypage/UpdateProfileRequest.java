package com.sagongsa.backend.mypage;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record UpdateProfileRequest(
	@Size(min = 2, max = 8, message = "닉네임은 2~8자여야 합니다.")
	@Pattern(regexp = "^[가-힣a-zA-Z0-9]+$", message = "닉네임은 한글, 영문, 숫자만 허용됩니다.")
	String nickname,

	@Size(min = 1, max = 10, message = "너구리 이름은 1~10자여야 합니다.")
	String raccoonName
) {}

package com.sagongsa.backend.social;

import com.sagongsa.backend.domain.enums.ReportCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record ReportRequest(
	@NotNull(message = "신고 사유 항목을 선택해 주세요.")
	ReportCategory category,
	@Size(max = 100, message = "신고 사유는 최대 100자까지 가능합니다.")
	String reason
) {}

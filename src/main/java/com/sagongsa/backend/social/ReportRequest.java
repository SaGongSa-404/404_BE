package com.sagongsa.backend.social;

import jakarta.validation.constraints.Size;

record ReportRequest(
    @Size(max = 100, message = "신고 사유는 최대 100자까지 가능합니다.")
    String reason
) {}

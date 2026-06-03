package com.sagongsa.backend.social;

import com.sagongsa.backend.auth.CurrentUserId;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class UserReportController {

    private final ReportService reportService;

    UserReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/{targetUserId}/reports")
    public ResponseEntity<Void> reportUser(
        @CurrentUserId UUID userId,
        @PathVariable UUID targetUserId,
        @Valid @RequestBody ReportRequest request) {
        reportService.reportUser(userId, targetUserId, request.category(), request.reason());
        return ResponseEntity.noContent().build();
    }
}

package com.fourohfour.backend.modules.adjustment.api.http;

import com.fourohfour.backend.modules.adjustment.application.AdjustmentService;
import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AdjustmentController {

    private final AdjustmentService adjustmentService;

    public AdjustmentController(AdjustmentService adjustmentService) {
        this.adjustmentService = adjustmentService;
    }

    @PostMapping("/houses/{houseId}/adjustments/substitute-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentRequestResponse requestSubstitute(Authentication authentication, @PathVariable UUID houseId, @Valid @RequestBody CreateAdjustmentRequest request) {
        return toResponse(adjustmentService.requestSubstitute(
                CurrentAuthenticatedUser.userId(authentication),
                houseId,
                new AdjustmentService.CreateAdjustmentCommand(request.choreInstanceId(), request.reason(), null, request.expiresAt())
        ));
    }

    @PostMapping("/houses/{houseId}/adjustments/reschedule-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentRequestResponse requestReschedule(Authentication authentication, @PathVariable UUID houseId, @Valid @RequestBody CreateAdjustmentRequest request) {
        return toResponse(adjustmentService.requestReschedule(
                CurrentAuthenticatedUser.userId(authentication),
                houseId,
                new AdjustmentService.CreateAdjustmentCommand(request.choreInstanceId(), request.reason(), request.requestedDate(), request.expiresAt())
        ));
    }

    @PostMapping("/adjustments/{adjustmentRequestId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(Authentication authentication, @PathVariable UUID adjustmentRequestId) {
        adjustmentService.acceptAdjustment(CurrentAuthenticatedUser.userId(authentication), adjustmentRequestId);
    }

    @PostMapping("/adjustments/{adjustmentRequestId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(Authentication authentication, @PathVariable UUID adjustmentRequestId) {
        adjustmentService.rejectAdjustment(CurrentAuthenticatedUser.userId(authentication), adjustmentRequestId);
    }

    @PostMapping("/adjustments/{adjustmentRequestId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(Authentication authentication, @PathVariable UUID adjustmentRequestId) {
        adjustmentService.cancelAdjustment(CurrentAuthenticatedUser.userId(authentication), adjustmentRequestId);
    }

    @GetMapping("/houses/{houseId}/adjustments/open")
    public List<AdjustmentRequestResponse> listOpen(Authentication authentication, @PathVariable UUID houseId) {
        return adjustmentService.listOpenRequests(CurrentAuthenticatedUser.userId(authentication), houseId).stream()
                .map(this::toResponse)
                .toList();
    }

    private AdjustmentRequestResponse toResponse(AdjustmentService.AdjustmentRequestView view) {
        return new AdjustmentRequestResponse(
                view.adjustmentRequestId(),
                view.houseId(),
                view.choreInstanceId(),
                view.requestType(),
                view.reason(),
                view.requestedDate(),
                view.status(),
                view.expiresAt(),
                view.choreTitle(),
                view.requesterName()
        );
    }

    public record CreateAdjustmentRequest(
            @NotNull UUID choreInstanceId,
            String reason,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requestedDate,
            Instant expiresAt
    ) {
    }

    public record AdjustmentRequestResponse(
            UUID adjustmentRequestId,
            UUID houseId,
            UUID choreInstanceId,
            String requestType,
            String reason,
            LocalDate requestedDate,
            String status,
            Instant expiresAt,
            String choreTitle,
            String requesterName
    ) {
    }
}


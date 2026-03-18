package com.fourohfour.backend.modules.review.api.http;

import com.fourohfour.backend.modules.auth.domain.CurrentAuthenticatedUser;
import com.fourohfour.backend.modules.review.application.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/houses/{houseId}/weekly-recaps")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/latest")
    public ReviewService.WeeklyRecapView getLatest(Authentication authentication, @PathVariable UUID houseId) {
        return reviewService.getLatestWeeklyRecap(CurrentAuthenticatedUser.userId(authentication), houseId);
    }

    @GetMapping
    public List<ReviewService.WeeklyRecapView> list(Authentication authentication, @PathVariable UUID houseId) {
        return reviewService.listPastWeeklyRecaps(CurrentAuthenticatedUser.userId(authentication), houseId);
    }

    @PostMapping("/{snapshotId}/satisfaction")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitSatisfaction(
            Authentication authentication,
            @PathVariable UUID houseId,
            @PathVariable UUID snapshotId,
            @Valid @RequestBody SubmitSatisfactionRequest request
    ) {
        reviewService.submitWeeklySatisfaction(CurrentAuthenticatedUser.userId(authentication), houseId, snapshotId, request.score(), request.comment());
    }

    public record SubmitSatisfactionRequest(
            @Min(1) @Max(5) int score,
            String comment
    ) {
    }
}


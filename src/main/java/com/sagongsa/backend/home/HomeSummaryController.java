package com.sagongsa.backend.home;

import com.sagongsa.backend.auth.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
@Tag(name = "Home", description = "Home screen summary API")
public class HomeSummaryController {

	private final HomeSummaryService homeSummaryService;

	public HomeSummaryController(HomeSummaryService homeSummaryService) {
		this.homeSummaryService = homeSummaryService;
	}

	@GetMapping("/summary")
	@Operation(
		summary = "Get home summary",
		description = "Returns user profile, mascot state, monthly budget summary, latest notifications, and rational choice rate for the home screen.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Home summary returned"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "403", description = "User cannot access this summary"),
			@ApiResponse(responseCode = "404", description = "User account does not exist")
		}
	)
	public HomeSummaryResponse getSummary(@Parameter(hidden = true) @CurrentUserId UUID userId) {
		return homeSummaryService.getSummary(userId);
	}

	@PostMapping("/budget-exhaustion-bubble/seen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void markBudgetExhaustionBubbleSeen(@CurrentUserId UUID userId) {
		homeSummaryService.markBubbleSeen(userId);
	}
}

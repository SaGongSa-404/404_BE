package com.sagongsa.backend.onboarding;

import com.sagongsa.backend.auth.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding", description = "Initial profile, mascot, budget, and survey completion API")
public class OnboardingController {

	private final OnboardingService onboardingService;

	public OnboardingController(OnboardingService onboardingService) {
		this.onboardingService = onboardingService;
	}

	@PostMapping("/complete")
	@Operation(
		summary = "Complete onboarding",
		description = "Stores the user's onboarding profile, mascot name, monthly budget, and survey choice.",
		responses = {
			@ApiResponse(responseCode = "200", description = "Onboarding completed"),
			@ApiResponse(responseCode = "400", description = "Invalid onboarding payload"),
			@ApiResponse(responseCode = "401", description = "Missing or invalid authentication"),
			@ApiResponse(responseCode = "403", description = "User is not allowed to complete onboarding"),
			@ApiResponse(responseCode = "404", description = "User account does not exist"),
			@ApiResponse(responseCode = "409", description = "Onboarding is already complete")
		}
	)
	public ResponseEntity<OnboardingCompleteResponse> complete(
		@Parameter(hidden = true) @CurrentUserId UUID userId,
		@RequestBody(required = false) OnboardingCompleteRequest request
	) {
		return ResponseEntity.ok(onboardingService.complete(userId, request));
	}
}

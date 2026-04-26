package com.sagongsa.backend.onboarding;

import com.sagongsa.backend.auth.CurrentUserId;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

	private final OnboardingService onboardingService;

	public OnboardingController(OnboardingService onboardingService) {
		this.onboardingService = onboardingService;
	}

	@PostMapping("/complete")
	public ResponseEntity<OnboardingCompleteResponse> complete(
		@CurrentUserId UUID userId,
		@RequestBody(required = false) OnboardingCompleteRequest request
	) {
		return ResponseEntity.ok(onboardingService.complete(userId, request));
	}
}

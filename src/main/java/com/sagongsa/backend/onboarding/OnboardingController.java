package com.sagongsa.backend.onboarding;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

	private static final String USER_ID_HEADER = "X-User-Id";

	private final OnboardingService onboardingService;

	public OnboardingController(OnboardingService onboardingService) {
		this.onboardingService = onboardingService;
	}

	@PostMapping("/complete")
	public ResponseEntity<OnboardingCompleteResponse> complete(
		@RequestHeader(name = USER_ID_HEADER, required = false) String userIdHeader,
		@RequestBody(required = false) OnboardingCompleteRequest request
	) {
		return ResponseEntity.ok(onboardingService.complete(parseUserId(userIdHeader), request));
	}

	private UUID parseUserId(String userIdHeader) {
		if (userIdHeader == null || userIdHeader.isBlank()) {
			throw new OnboardingBadRequestException("X-User-Id header is required.");
		}

		try {
			return UUID.fromString(userIdHeader.trim());
		} catch (IllegalArgumentException exception) {
			throw new OnboardingBadRequestException("X-User-Id header must be a UUID.");
		}
	}
}

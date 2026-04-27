package com.sagongsa.backend.onboarding;

import java.util.UUID;

public record OnboardingCompleteResponse(
	UUID userId,
	String onboardingStatus,
	String budgetYearMonth,
	UUID surveyResponseSetId
) {
}

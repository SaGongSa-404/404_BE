package com.sagongsa.backend.onboarding;

public record OnboardingCompleteRequest(
	String nickname,
	String mascotName,
	String timezone,
	Integer monthlyBudgetAmount,
	String regretFrequencyChoice
) {
}

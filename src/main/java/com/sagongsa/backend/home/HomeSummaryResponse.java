package com.sagongsa.backend.home;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HomeSummaryResponse(
	UserProfileSummary userProfile,
	MascotSummary mascot,
	BudgetSummary budget,
	BubbleSummary bubble,
	NotificationsSummary notifications,
	BigDecimal rationalChoiceRate
) {

	public record UserProfileSummary(
		String nickname,
		String mascotName,
		String timezone
	) {
	}

	public record MascotSummary(
		String state,
		String lastReactionMessage,
		Instant lastStateChangedAt,
		Instant reactionExpiresAt
	) {
	}

	public record BudgetSummary(
		String yearMonth,
		int monthlyBudgetAmount,
		int spentAmount,
		int remainingAmount,
		BigDecimal warningThresholdRate,
		boolean exhausted,
		boolean showBudgetExhaustionBubble
	) {
	}

	public record BubbleSummary(
		String type,
		String message,
		int priority,
		boolean shouldShow,
		String seenEndpoint
	) {
	}

	public record NotificationsSummary(
		long unreadCount,
		List<NotificationSummary> latestNotifications
	) {
	}

	public record NotificationSummary(
		UUID id,
		String type,
		String title,
		String body,
		String targetPath,
		boolean read,
		Instant createdAt
	) {
	}
}

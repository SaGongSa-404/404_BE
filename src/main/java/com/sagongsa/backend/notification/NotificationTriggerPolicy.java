package com.sagongsa.backend.notification;

import com.sagongsa.backend.notification.NotificationTriggerTargets.*;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

/** 알림 문구, 이동 경로, 중복 키와 월초 발행 시각을 DB 없이 결정한다. */
final class NotificationTriggerPolicy {
	private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

	private NotificationTriggerPolicy() {
	}

	static NotificationPublishRequest voteSummary(VotePostTarget target) {
		return new NotificationPublishRequest(
			target.userId(),
			"SOCIAL_VOTE_SUMMARY",
			"투표 결과 요약",
			"🗳️24시간 동안 총 %d명이 투표했어요! 결과 확인해보세요".formatted(target.voteCount()),
			target.itemId(),
			null,
			null,
			"/social/posts/" + target.postId(),
			"post:" + target.postId(),
			NotificationChannels.SOCIAL_ACTIVITY
		);
	}

	static NotificationPublishRequest decisionNudge(VotePostTarget target) {
		return new NotificationPublishRequest(
			target.userId(),
			"SOCIAL_DECISION_NUDGE",
			"투표 결정 넛지",
			"🛒위시템에 총 %d명이 투표했어요. 슬슬 결정해볼까요?".formatted(target.voteCount()),
			target.itemId(),
			null,
			null,
			"/social/posts/" + target.postId(),
			"post:" + target.postId(),
			NotificationChannels.SOCIAL_ACTIVITY
		);
	}

	static NotificationPublishRequest regretFollowUp(RegretFollowUpTarget target) {
		return new NotificationPublishRequest(
			target.userId(),
			"REGRET_CHECK_FOLLOW_UP",
			"위시 돌아보기",
			NotificationMessages.regretCheckFollowUpBody(target.itemTitle()),
			target.itemId(),
			target.decisionId(),
			target.reminderId(),
			"/reflections?decisionId=" + target.decisionId(),
			"decision:" + target.decisionId(),
			NotificationChannels.CONSUMPTION_MANAGEMENT
		);
	}

	static NotificationPublishRequest wishlistReminder(WishlistReminderTarget target) {
		return new NotificationPublishRequest(
			target.userId(),
			"WISHLIST_REMINDER",
			"리마인드",
			"아직 결정 못 한 위시템이 기다리고 있어요! 같이 고민해볼까요?",
			target.itemId(),
			null,
			null,
			"/wishlist/items/" + target.itemId(),
			"item:" + target.itemId(),
			NotificationChannels.CONSUMPTION_MANAGEMENT
		);
	}

	static NotificationPublishRequest budgetReset(UUID userId, String yearMonth) {
		return new NotificationPublishRequest(
			userId,
			"BUDGET_RESET",
			"예산 리셋",
			"🌤️새달이 시작됐어요! 이번 달도 신중하게 골라봐요",
			null,
			null,
			null,
			"/home",
			"month:" + yearMonth,
			NotificationChannels.CONSUMPTION_MANAGEMENT
		);
	}

	static BudgetResetWindow budgetResetWindow(OffsetDateTime now) {
		var koreaNow = now.atZoneSameInstant(KOREA_ZONE_ID);
		YearMonth yearMonthValue = YearMonth.from(koreaNow);
		var dueAt = yearMonthValue.atDay(1).atTime(LocalTime.of(9, 0)).atZone(KOREA_ZONE_ID);
		if (koreaNow.isBefore(dueAt)) {
			return null;
		}

		String yearMonth = yearMonthValue.toString();
		OffsetDateTime dueAtUtc = dueAt.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
		return new BudgetResetWindow(yearMonth, dueAtUtc);
	}

	record BudgetResetWindow(String yearMonth, OffsetDateTime dueAtUtc) {
	}
}

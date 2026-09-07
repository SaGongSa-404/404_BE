package com.sagongsa.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.sagongsa.backend.notification.NotificationTriggerTargets.VotePostTarget;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTriggerPolicyTest {

	@Test
	void budgetResetStartsAtNineInKoreaAndRemainsDueAfterward() {
		assertThat(NotificationTriggerPolicy.budgetResetWindow(OffsetDateTime.parse("2026-09-01T08:59:59+09:00"))).isNull();
		var atBoundary = NotificationTriggerPolicy.budgetResetWindow(OffsetDateTime.parse("2026-09-01T00:00:00Z"));
		assertThat(atBoundary.yearMonth()).isEqualTo("2026-09");
		assertThat(atBoundary.dueAtUtc()).isEqualTo(OffsetDateTime.parse("2026-09-01T00:00:00Z"));
		assertThat(NotificationTriggerPolicy.budgetResetWindow(OffsetDateTime.parse("2026-09-07T12:00:00Z"))).isEqualTo(atBoundary);
	}

	@Test
	void voteMessagesKeepIndependentTypesWithSamePostDedupeKey() {
		var target = new VotePostTarget(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 12);
		var summary = NotificationTriggerPolicy.voteSummary(target);
		var nudge = NotificationTriggerPolicy.decisionNudge(target);
		assertThat(summary.notificationType()).isEqualTo("SOCIAL_VOTE_SUMMARY");
		assertThat(nudge.notificationType()).isEqualTo("SOCIAL_DECISION_NUDGE");
		assertThat(summary.dedupeKey()).isEqualTo("post:" + target.postId()).isEqualTo(nudge.dedupeKey());
		assertThat(summary.targetPath()).isEqualTo("/social/posts/" + target.postId());
		assertThat(summary.body()).isEqualTo("🗳️24시간 동안 총 12명이 투표했어요! 결과 확인해보세요");
		assertThat(summary.userId()).isEqualTo(target.userId());
		assertThat(summary.itemId()).isEqualTo(target.itemId());
	}
}

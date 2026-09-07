package com.sagongsa.backend.notification;

import com.sagongsa.backend.domain.budget.BudgetCycleRolloverService;
import com.sagongsa.backend.notification.NotificationTriggerTargets.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class NotificationTriggerWorker {

	private final NotificationTriggerQueries queries;
	private final NotificationPublisher notificationPublisher;
	private final BudgetCycleRolloverService budgetCycleRolloverService;

	public NotificationTriggerWorker(
		NotificationTriggerQueries targets,
		NotificationPublisher notificationPublisher,
		BudgetCycleRolloverService budgetCycleRolloverService
	) {
		this.queries = targets;
		this.notificationPublisher = notificationPublisher;
		this.budgetCycleRolloverService = budgetCycleRolloverService;
	}

	public int processDueNotifications() {
		return processDueNotifications(OffsetDateTime.now(ZoneOffset.UTC));
	}

	int processDueNotifications(OffsetDateTime now) {
		int createdCount = 0;
		createdCount += publishVoteSummaries(now);
		createdCount += publishDecisionNudges(now);
		createdCount += publishRegretFollowUps(now.minusDays(2));
		createdCount += publishWishlistReminders(now.minusDays(3));
		createdCount += publishBudgetResetIfDue(now);
		return createdCount;
	}

	private int publishVoteSummaries(OffsetDateTime now) {
		List<VotePostTarget> targets = queries.voteSummaries(now);

		int createdCount = 0;
		for (VotePostTarget target : targets) {
			if (publish(NotificationTriggerPolicy.voteSummary(target))) {
				createdCount++;
			}
		}
		return createdCount;
	}

	private int publishDecisionNudges(OffsetDateTime now) {
		List<VotePostTarget> targets = queries.decisionNudges(now);

		int createdCount = 0;
		for (VotePostTarget target : targets) {
			if (publish(NotificationTriggerPolicy.decisionNudge(target))) {
				createdCount++;
			}
		}
		return createdCount;
	}

	private int publishRegretFollowUps(OffsetDateTime cutoff) {
		List<RegretFollowUpTarget> targets = queries.regretFollowUps(cutoff);

		int createdCount = 0;
		for (RegretFollowUpTarget target : targets) {
			if (publish(NotificationTriggerPolicy.regretFollowUp(target))) {
				createdCount++;
			}
		}
		return createdCount;
	}

	private int publishWishlistReminders(OffsetDateTime cutoff) {
		List<WishlistReminderTarget> targets = queries.wishlistReminders(cutoff);

		int createdCount = 0;
		for (WishlistReminderTarget target : targets) {
			if (publish(NotificationTriggerPolicy.wishlistReminder(target))) {
				createdCount++;
			}
		}
		return createdCount;
	}

	private int publishBudgetResetIfDue(OffsetDateTime now) {
		var window = NotificationTriggerPolicy.budgetResetWindow(now);
		if (window == null) {
			return 0;
		}
		String yearMonth = window.yearMonth();
		OffsetDateTime dueAtUtc = window.dueAtUtc();
		List<UUID> userIds = queries.budgetResets(dueAtUtc, yearMonth);

		int createdCount = 0;
		for (UUID userId : userIds) {
			budgetCycleRolloverService.ensureBudgetCycle(userId, yearMonth);
			if (publish(NotificationTriggerPolicy.budgetReset(userId, yearMonth))) {
				createdCount++;
			}
		}
		return createdCount;
	}

	private boolean publish(NotificationPublishRequest request) {
		return notificationPublisher.publish(request).created();
	}

}

package com.sagongsa.backend.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationTriggerWorker {

	private static final int BATCH_SIZE = 50;
	private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");
	private static final int RETENTION_MONTHS = 1;

	private final JdbcTemplate jdbcTemplate;
	private final NotificationPublisher notificationPublisher;

	public NotificationTriggerWorker(JdbcTemplate jdbcTemplate, NotificationPublisher notificationPublisher) {
		this.jdbcTemplate = jdbcTemplate;
		this.notificationPublisher = notificationPublisher;
	}

	public int processDueNotifications() {
		return processDueNotifications(OffsetDateTime.now(ZoneOffset.UTC));
	}

	int processDueNotifications(OffsetDateTime now) {
		deleteExpiredNotifications(now);
		int createdCount = 0;
		createdCount += publishVoteSummaries(now);
		createdCount += publishDecisionNudges(now);
		createdCount += publishRegretFollowUps(now.minusDays(2));
		createdCount += publishWishlistReminders(now.minusDays(3));
		createdCount += publishBudgetResetIfDue(now);
		return createdCount;
	}

	private void deleteExpiredNotifications(OffsetDateTime now) {
		jdbcTemplate.update(
			"delete from notifications where created_at < ?",
			now.minusMonths(RETENTION_MONTHS)
		);
	}

	private int publishVoteSummaries(OffsetDateTime now) {
		List<VotePostTarget> targets = jdbcTemplate.query(
			"""
			select fp.id as post_id,
			       fp.user_id,
			       fp.item_id,
			       count(pv.id)::int as vote_count
			from feed_posts fp
			join users u on u.id = fp.user_id
			join post_votes pv on pv.post_id = fp.id
			                  and pv.canceled_at is null
			                  and pv.created_at <= fp.created_at + interval '24 hours'
			where fp.deleted_at is null
			  and fp.moderation_status = 'ACTIVE'
			  and fp.created_at + interval '24 hours' <= ?
			  and u.status = 'ACTIVE'
			  and u.onboarding_status = 'COMPLETED'
			  and not exists (
			      select 1
			      from notifications n
			      where n.user_id = fp.user_id
			        and n.notification_type = 'SOCIAL_VOTE_SUMMARY'
			        and n.dedupe_key = 'post:' || fp.id::text
			  )
			group by fp.id, fp.user_id, fp.item_id, fp.created_at
			order by fp.created_at asc, fp.id asc
			limit ?
			""",
			this::mapVotePostTarget,
			now,
			BATCH_SIZE
		);

		int createdCount = 0;
		for (VotePostTarget target : targets) {
			if (publish(new NotificationPublishRequest(
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
			))) {
				createdCount++;
			}
		}
		return createdCount;
	}

	private int publishDecisionNudges(OffsetDateTime now) {
		List<VotePostTarget> targets = jdbcTemplate.query(
			"""
			select fp.id as post_id,
			       fp.user_id,
			       fp.item_id,
			       count(pv.id)::int as vote_count
			from feed_posts fp
			join users u on u.id = fp.user_id
			join saved_items si on si.id = fp.item_id
			                   and si.status = 'SAVED'
			join post_votes pv on pv.post_id = fp.id
			                  and pv.canceled_at is null
			                  and pv.created_at <= fp.created_at + interval '7 days'
			where fp.deleted_at is null
			  and fp.moderation_status = 'ACTIVE'
			  and fp.created_at + interval '7 days' <= ?
			  and u.status = 'ACTIVE'
			  and u.onboarding_status = 'COMPLETED'
			  and not exists (
			      select 1
			      from notifications n
			      where n.user_id = fp.user_id
			        and n.notification_type = 'SOCIAL_DECISION_NUDGE'
			        and n.dedupe_key = 'post:' || fp.id::text
			  )
			group by fp.id, fp.user_id, fp.item_id, fp.created_at
			order by fp.created_at asc, fp.id asc
			limit ?
			""",
			this::mapVotePostTarget,
			now,
			BATCH_SIZE
		);

		int createdCount = 0;
		for (VotePostTarget target : targets) {
			if (publish(new NotificationPublishRequest(
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
			))) {
				createdCount++;
			}
		}
		return createdCount;
	}

	private int publishRegretFollowUps(OffsetDateTime cutoff) {
		List<RegretFollowUpTarget> targets = jdbcTemplate.query(
			"""
			select n.user_id,
			       n.item_id,
			       n.decision_id,
			       n.reminder_id,
			       si.title as item_title
			from notifications n
			join users u on u.id = n.user_id
			join saved_items si on si.id = n.item_id
			where n.notification_type = 'REGRET_CHECK_READY'
			  and n.created_at <= ?
			  and n.decision_id is not null
			  and u.status = 'ACTIVE'
			  and u.onboarding_status = 'COMPLETED'
			  and not exists (
			      select 1
			      from purchase_reflections pr
			      where pr.decision_id = n.decision_id
			  )
			  and not exists (
			      select 1
			      from notifications f
			      where f.user_id = n.user_id
			        and f.notification_type = 'REGRET_CHECK_FOLLOW_UP'
			        and f.dedupe_key = 'decision:' || n.decision_id::text
			  )
			order by n.created_at asc, n.id asc
			limit ?
			""",
			this::mapRegretFollowUpTarget,
			cutoff,
			BATCH_SIZE
		);

		int createdCount = 0;
		for (RegretFollowUpTarget target : targets) {
			if (publish(new NotificationPublishRequest(
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
			))) {
				createdCount++;
			}
		}
		return createdCount;
	}

	private int publishWishlistReminders(OffsetDateTime cutoff) {
		List<WishlistReminderTarget> targets = jdbcTemplate.query(
			"""
			select si.id as item_id,
			       si.user_id
			from saved_items si
			join users u on u.id = si.user_id
			where si.status = 'SAVED'
			  and si.created_at <= ?
			  and u.status = 'ACTIVE'
			  and u.onboarding_status = 'COMPLETED'
			  and not exists (
			      select 1
			      from notifications n
			      where n.user_id = si.user_id
			        and n.notification_type = 'WISHLIST_REMINDER'
			        and n.dedupe_key = 'item:' || si.id::text
			  )
			order by si.created_at asc, si.id asc
			limit ?
			""",
			this::mapWishlistReminderTarget,
			cutoff,
			BATCH_SIZE
		);

		int createdCount = 0;
		for (WishlistReminderTarget target : targets) {
			if (publish(new NotificationPublishRequest(
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
			))) {
				createdCount++;
			}
		}
		return createdCount;
	}

	private int publishBudgetResetIfDue(OffsetDateTime now) {
		var koreaNow = now.atZoneSameInstant(KOREA_ZONE_ID);
		YearMonth yearMonthValue = YearMonth.from(koreaNow);
		var dueAt = yearMonthValue.atDay(1).atTime(LocalTime.of(9, 0)).atZone(KOREA_ZONE_ID);
		if (koreaNow.isBefore(dueAt)) {
			return 0;
		}

		String yearMonth = yearMonthValue.toString();
		OffsetDateTime dueAtUtc = dueAt.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
		List<UUID> userIds = jdbcTemplate.query(
			"""
			select u.id
			from users u
			where u.status = 'ACTIVE'
			  and u.onboarding_status = 'COMPLETED'
			  and exists (
			      select 1
			      from budget_cycles bc
			      where bc.user_id = u.id
			        and bc.created_at <= ?
			  )
			  and not exists (
			      select 1
			      from notifications n
			      where n.user_id = u.id
			        and n.notification_type = 'BUDGET_RESET'
			        and n.dedupe_key = 'month:' || ?::text
			  )
			order by u.created_at asc, u.id asc
			limit ?
			""",
			(rs, rowNumber) -> rs.getObject("id", UUID.class),
			dueAtUtc,
			yearMonth,
			BATCH_SIZE
		);

		int createdCount = 0;
		for (UUID userId : userIds) {
			if (publish(new NotificationPublishRequest(
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
			))) {
				createdCount++;
			}
		}
		return createdCount;
	}

	private boolean publish(NotificationPublishRequest request) {
		return notificationPublisher.publish(request).created();
	}

	private VotePostTarget mapVotePostTarget(ResultSet rs, int rowNumber) throws SQLException {
		return new VotePostTarget(
			rs.getObject("post_id", UUID.class),
			rs.getObject("user_id", UUID.class),
			rs.getObject("item_id", UUID.class),
			rs.getInt("vote_count")
		);
	}

	private RegretFollowUpTarget mapRegretFollowUpTarget(ResultSet rs, int rowNumber) throws SQLException {
		return new RegretFollowUpTarget(
			rs.getObject("user_id", UUID.class),
			rs.getObject("item_id", UUID.class),
			rs.getObject("decision_id", UUID.class),
			rs.getObject("reminder_id", UUID.class),
			rs.getString("item_title")
		);
	}

	private WishlistReminderTarget mapWishlistReminderTarget(ResultSet rs, int rowNumber) throws SQLException {
		return new WishlistReminderTarget(
			rs.getObject("item_id", UUID.class),
			rs.getObject("user_id", UUID.class)
		);
	}

	private record VotePostTarget(UUID postId, UUID userId, UUID itemId, int voteCount) {
	}

	private record RegretFollowUpTarget(
		UUID userId,
		UUID itemId,
		UUID decisionId,
		UUID reminderId,
		String itemTitle
	) {
	}

	private record WishlistReminderTarget(UUID itemId, UUID userId) {
	}
}

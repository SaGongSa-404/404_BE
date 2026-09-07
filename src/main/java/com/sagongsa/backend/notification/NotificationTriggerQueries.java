package com.sagongsa.backend.notification;

import com.sagongsa.backend.notification.NotificationTriggerTargets.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 대상 선정의 join, 시간 조건, 중복 제외와 batch 상한을 소유한다. */
@Repository
class NotificationTriggerQueries {
	private static final int BATCH_SIZE = 50;
	private final JdbcTemplate jdbcTemplate;

	NotificationTriggerQueries(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	List<VotePostTarget> voteSummaries(OffsetDateTime now) {
		return jdbcTemplate.query(
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
	}

	List<VotePostTarget> decisionNudges(OffsetDateTime now) {
		return jdbcTemplate.query(
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
	}

	List<RegretFollowUpTarget> regretFollowUps(OffsetDateTime cutoff) {
		return jdbcTemplate.query(
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
	}

	List<WishlistReminderTarget> wishlistReminders(OffsetDateTime cutoff) {
		return jdbcTemplate.query(
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
	}

	List<UUID> budgetResets(OffsetDateTime dueAtUtc, String yearMonth) {
		return jdbcTemplate.query(
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

}

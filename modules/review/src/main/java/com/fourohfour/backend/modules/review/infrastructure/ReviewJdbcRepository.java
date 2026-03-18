package com.fourohfour.backend.modules.review.infrastructure;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewJdbcRepository {

    private final JdbcClient jdbcClient;

    public ReviewJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<UUID> listActiveHouseIds() {
        return jdbcClient.sql("""
                        select id
                        from houses
                        where status = 'ACTIVE'
                        order by created_at asc
                        """)
                .query((rs, rowNum) -> UUID.fromString(rs.getString("id")))
                .list();
    }

    public Optional<WeeklySnapshotRecord> findLatestSnapshot(UUID houseId) {
        return jdbcClient.sql("""
                        select ws.id,
                               ws.house_id,
                               ws.week_start_date,
                               ws.week_end_date,
                               ws.status,
                               ws.generated_at,
                               whs.total_chores,
                               whs.completed_chores,
                               whs.completion_rate,
                               whs.accepted_adjustments
                        from weekly_snapshots ws
                        left join weekly_house_stats whs on whs.snapshot_id = ws.id
                        where ws.house_id = :houseId
                        order by ws.week_start_date desc
                        limit 1
                        """)
                .param("houseId", houseId)
                .query((rs, rowNum) -> new WeeklySnapshotRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        rs.getDate("week_start_date").toLocalDate(),
                        rs.getDate("week_end_date").toLocalDate(),
                        rs.getString("status"),
                        rs.getTimestamp("generated_at").toInstant(),
                        rs.getInt("total_chores"),
                        rs.getInt("completed_chores"),
                        rs.getBigDecimal("completion_rate") == null ? 0.0 : rs.getBigDecimal("completion_rate").doubleValue(),
                        rs.getInt("accepted_adjustments")
                ))
                .optional();
    }

    public List<WeeklySnapshotRecord> listSnapshots(UUID houseId) {
        return jdbcClient.sql("""
                        select ws.id,
                               ws.house_id,
                               ws.week_start_date,
                               ws.week_end_date,
                               ws.status,
                               ws.generated_at,
                               whs.total_chores,
                               whs.completed_chores,
                               whs.completion_rate,
                               whs.accepted_adjustments
                        from weekly_snapshots ws
                        left join weekly_house_stats whs on whs.snapshot_id = ws.id
                        where ws.house_id = :houseId
                        order by ws.week_start_date desc
                        """)
                .param("houseId", houseId)
                .query((rs, rowNum) -> new WeeklySnapshotRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        rs.getDate("week_start_date").toLocalDate(),
                        rs.getDate("week_end_date").toLocalDate(),
                        rs.getString("status"),
                        rs.getTimestamp("generated_at").toInstant(),
                        rs.getInt("total_chores"),
                        rs.getInt("completed_chores"),
                        rs.getBigDecimal("completion_rate") == null ? 0.0 : rs.getBigDecimal("completion_rate").doubleValue(),
                        rs.getInt("accepted_adjustments")
                ))
                .list();
    }

    public List<WeeklyMemberStatsRecord> listMemberStats(UUID snapshotId) {
        return jdbcClient.sql("""
                        select wms.membership_id,
                               wms.assigned_chores,
                               wms.completed_chores,
                               wms.completion_rate,
                               wms.substitute_acceptances,
                               up.nickname
                        from weekly_member_stats wms
                        join house_memberships hm on hm.id = wms.membership_id
                        join user_profiles up on up.user_id = hm.user_id
                        where wms.snapshot_id = :snapshotId
                        order by up.nickname asc
                        """)
                .param("snapshotId", snapshotId)
                .query((rs, rowNum) -> new WeeklyMemberStatsRecord(
                        UUID.fromString(rs.getString("membership_id")),
                        rs.getString("nickname"),
                        rs.getInt("assigned_chores"),
                        rs.getInt("completed_chores"),
                        rs.getBigDecimal("completion_rate") == null ? 0.0 : rs.getBigDecimal("completion_rate").doubleValue(),
                        rs.getInt("substitute_acceptances")
                ))
                .list();
    }

    public boolean hasSubmittedSatisfaction(UUID snapshotId, UUID userId) {
        Integer count = jdbcClient.sql("""
                        select count(*)
                        from weekly_satisfactions
                        where snapshot_id = :snapshotId
                          and user_id = :userId
                        """)
                .param("snapshotId", snapshotId)
                .param("userId", userId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    public void submitSatisfaction(UUID snapshotId, UUID userId, int score, String comment, Instant now) {
        jdbcClient.sql("""
                        insert into weekly_satisfactions (
                            id, snapshot_id, user_id, score, comment, submitted_at, created_at, updated_at
                        )
                        values (:id, :snapshotId, :userId, :score, :comment, :submittedAt, :createdAt, :updatedAt)
                        on conflict (snapshot_id, user_id)
                        do update set score = excluded.score,
                                      comment = excluded.comment,
                                      submitted_at = excluded.submitted_at,
                                      updated_at = excluded.updated_at
                        """)
                .param("id", UUID.randomUUID())
                .param("snapshotId", snapshotId)
                .param("userId", userId)
                .param("score", score)
                .param("comment", comment)
                .param("submittedAt", Timestamp.from(now))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public Optional<UUID> findSnapshotIdByHouseAndWeek(UUID houseId, LocalDate weekStartDate) {
        return jdbcClient.sql("""
                        select id
                        from weekly_snapshots
                        where house_id = :houseId
                          and week_start_date = :weekStartDate
                        limit 1
                        """)
                .param("houseId", houseId)
                .param("weekStartDate", Date.valueOf(weekStartDate))
                .query((rs, rowNum) -> UUID.fromString(rs.getString("id")))
                .optional();
    }

    public UUID createSnapshot(UUID houseId, LocalDate weekStartDate, LocalDate weekEndDate, Instant now) {
        UUID snapshotId = UUID.randomUUID();
        jdbcClient.sql("""
                        insert into weekly_snapshots (
                            id, house_id, week_start_date, week_end_date, status, generated_at, created_at, updated_at
                        )
                        values (:id, :houseId, :weekStartDate, :weekEndDate, 'READY', :generatedAt, :createdAt, :updatedAt)
                        """)
                .param("id", snapshotId)
                .param("houseId", houseId)
                .param("weekStartDate", Date.valueOf(weekStartDate))
                .param("weekEndDate", Date.valueOf(weekEndDate))
                .param("generatedAt", Timestamp.from(now))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
        return snapshotId;
    }

    public void replaceHouseStats(UUID snapshotId, UUID houseId, LocalDate weekStartDate, LocalDate weekEndDate, Instant now) {
        jdbcClient.sql("delete from weekly_house_stats where snapshot_id = :snapshotId")
                .param("snapshotId", snapshotId)
                .update();

        jdbcClient.sql("""
                        insert into weekly_house_stats (
                            id, snapshot_id, total_chores, completed_chores, completion_rate, accepted_adjustments, created_at, updated_at
                        )
                        select gen_random_uuid(),
                               :snapshotId,
                               count(*) as total_chores,
                               count(case when ci.status = 'COMPLETED' then 1 end) as completed_chores,
                               case when count(*) = 0 then 0
                                    else round((count(case when ci.status = 'COMPLETED' then 1 end)::numeric / count(*)::numeric) * 100, 2)
                               end as completion_rate,
                               (
                                   select count(*)
                                   from adjustment_requests ar
                                   where ar.house_id = :houseId
                                     and ar.status = 'ACCEPTED'
                                     and ar.updated_at >= :weekStartAt
                                     and ar.updated_at < :weekEndExclusive
                               ) as accepted_adjustments,
                               :createdAt,
                               :updatedAt
                        from chore_instances ci
                        where ci.house_id = :houseId
                          and ci.scheduled_date >= :weekStartDate
                          and ci.scheduled_date <= :weekEndDate
                        """)
                .param("snapshotId", snapshotId)
                .param("houseId", houseId)
                .param("weekStartAt", Timestamp.valueOf(weekStartDate.atStartOfDay()))
                .param("weekEndExclusive", Timestamp.valueOf(weekEndDate.plusDays(1).atStartOfDay()))
                .param("weekStartDate", Date.valueOf(weekStartDate))
                .param("weekEndDate", Date.valueOf(weekEndDate))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void replaceMemberStats(UUID snapshotId, UUID houseId, LocalDate weekStartDate, LocalDate weekEndDate, Instant now) {
        jdbcClient.sql("delete from weekly_member_stats where snapshot_id = :snapshotId")
                .param("snapshotId", snapshotId)
                .update();

        jdbcClient.sql("""
                        insert into weekly_member_stats (
                            id, snapshot_id, membership_id, assigned_chores, completed_chores, completion_rate, substitute_acceptances, created_at, updated_at
                        )
                        select gen_random_uuid(),
                               :snapshotId,
                               hm.id as membership_id,
                               count(ci.id) as assigned_chores,
                               count(case when ci.status = 'COMPLETED' then 1 end) as completed_chores,
                               case when count(ci.id) = 0 then 0
                                    else round((count(case when ci.status = 'COMPLETED' then 1 end)::numeric / count(ci.id)::numeric) * 100, 2)
                               end as completion_rate,
                               coalesce(arc.accepted_substitute_count, 0) as substitute_acceptances,
                               :createdAt,
                               :updatedAt
                        from house_memberships hm
                        left join chore_instances ci
                          on ci.current_assignee_membership_id = hm.id
                         and ci.house_id = hm.house_id
                         and ci.scheduled_date >= :weekStartDate
                         and ci.scheduled_date <= :weekEndDate
                        left join adjustment_reward_counters arc
                          on arc.membership_id = hm.id
                        where hm.house_id = :houseId
                          and hm.status = 'ACTIVE'
                        group by hm.id, arc.accepted_substitute_count
                        """)
                .param("snapshotId", snapshotId)
                .param("houseId", houseId)
                .param("weekStartDate", Date.valueOf(weekStartDate))
                .param("weekEndDate", Date.valueOf(weekEndDate))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public record WeeklySnapshotRecord(
            UUID snapshotId,
            UUID houseId,
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            String status,
            Instant generatedAt,
            int totalChores,
            int completedChores,
            double completionRate,
            int acceptedAdjustments
    ) {
    }

    public record WeeklyMemberStatsRecord(
            UUID membershipId,
            String nickname,
            int assignedChores,
            int completedChores,
            double completionRate,
            int substituteAcceptances
    ) {
    }
}


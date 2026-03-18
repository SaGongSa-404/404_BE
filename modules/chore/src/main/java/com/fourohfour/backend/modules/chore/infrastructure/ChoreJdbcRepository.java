package com.fourohfour.backend.modules.chore.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ChoreJdbcRepository {

    private final JdbcClient jdbcClient;

    public ChoreJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void createChoreRule(
            UUID choreRuleId,
            UUID houseId,
            UUID spaceId,
            String title,
            String description,
            UUID defaultAssigneeMembershipId,
            Integer estimatedMinutes,
            Instant now
    ) {
        jdbcClient.sql("""
                        insert into chore_rules (
                            id, house_id, space_id, title, description, default_assignee_membership_id, estimated_minutes, status, created_at, updated_at
                        )
                        values (:id, :houseId, :spaceId, :title, :description, :defaultAssigneeMembershipId, :estimatedMinutes, 'ACTIVE', :createdAt, :updatedAt)
                        """)
                .param("id", choreRuleId)
                .param("houseId", houseId)
                .param("spaceId", spaceId)
                .param("title", title)
                .param("description", description)
                .param("defaultAssigneeMembershipId", defaultAssigneeMembershipId)
                .param("estimatedMinutes", estimatedMinutes)
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void updateChoreRule(UUID choreRuleId, String title, String description, Integer estimatedMinutes, Instant now) {
        jdbcClient.sql("""
                        update chore_rules
                        set title = :title,
                            description = :description,
                            estimated_minutes = :estimatedMinutes,
                            updated_at = :updatedAt
                        where id = :choreRuleId
                          and status = 'ACTIVE'
                        """)
                .param("choreRuleId", choreRuleId)
                .param("title", title)
                .param("description", description)
                .param("estimatedMinutes", estimatedMinutes)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void archiveChoreRule(UUID choreRuleId, Instant now) {
        jdbcClient.sql("""
                        update chore_rules
                        set status = 'ARCHIVED',
                            updated_at = :updatedAt
                        where id = :choreRuleId
                        """)
                .param("choreRuleId", choreRuleId)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public Optional<ChoreRuleRecord> findChoreRule(UUID choreRuleId) {
        return jdbcClient.sql("""
                        select id, house_id, space_id, title, description, default_assignee_membership_id, estimated_minutes
                        from chore_rules
                        where id = :choreRuleId
                          and status = 'ACTIVE'
                        limit 1
                        """)
                .param("choreRuleId", choreRuleId)
                .query((rs, rowNum) -> new ChoreRuleRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        UUID.fromString(rs.getString("space_id")),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("default_assignee_membership_id") == null ? null : UUID.fromString(rs.getString("default_assignee_membership_id")),
                        rs.getInt("estimated_minutes")
                ))
                .optional();
    }

    public void createRecurrenceRule(UUID recurrenceRuleId, UUID choreRuleId, String frequency, Integer intervalValue, String[] daysOfWeek, LocalDate startDate, Instant now) {
        jdbcClient.sql("""
                        insert into recurrence_rules (
                            id, chore_rule_id, frequency, interval_value, days_of_week, start_date, end_date, created_at, updated_at
                        )
                        values (:id, :choreRuleId, :frequency, :intervalValue, :daysOfWeek, :startDate, null, :createdAt, :updatedAt)
                        """)
                .param("id", recurrenceRuleId)
                .param("choreRuleId", choreRuleId)
                .param("frequency", frequency)
                .param("intervalValue", intervalValue)
                .param("daysOfWeek", daysOfWeek)
                .param("startDate", startDate)
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void createChoreInstance(
            UUID choreInstanceId,
            UUID houseId,
            UUID choreRuleId,
            LocalDate scheduledDate,
            UUID currentAssigneeMembershipId,
            String originType,
            Instant now
    ) {
        jdbcClient.sql("""
                        insert into chore_instances (
                            id, house_id, chore_rule_id, scheduled_date, status, current_assignee_membership_id, origin_type, created_at, updated_at
                        )
                        values (:id, :houseId, :choreRuleId, :scheduledDate, 'PENDING', :currentAssigneeMembershipId, :originType, :createdAt, :updatedAt)
                        """)
                .param("id", choreInstanceId)
                .param("houseId", houseId)
                .param("choreRuleId", choreRuleId)
                .param("scheduledDate", scheduledDate)
                .param("currentAssigneeMembershipId", currentAssigneeMembershipId)
                .param("originType", originType)
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void createChoreAssignment(UUID assignmentId, UUID choreInstanceId, UUID assigneeMembershipId, UUID assignedByUserId, String assignmentType, Instant now) {
        jdbcClient.sql("""
                        insert into chore_assignments (
                            id, chore_instance_id, assignee_membership_id, assigned_by_user_id, assigned_at, assignment_type, created_at, updated_at
                        )
                        values (:id, :choreInstanceId, :assigneeMembershipId, :assignedByUserId, :assignedAt, :assignmentType, :createdAt, :updatedAt)
                        """)
                .param("id", assignmentId)
                .param("choreInstanceId", choreInstanceId)
                .param("assigneeMembershipId", assigneeMembershipId)
                .param("assignedByUserId", assignedByUserId)
                .param("assignedAt", Timestamp.from(now))
                .param("assignmentType", assignmentType)
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public List<TodayChoreRecord> listTodayChores(UUID houseId, LocalDate date) {
        return jdbcClient.sql("""
                        select ci.id as chore_instance_id,
                               ci.house_id,
                               ci.status,
                               ci.scheduled_date,
                               cr.title,
                               s.name as space_name,
                               up.nickname as assignee_name,
                               cc.id as completion_id
                        from chore_instances ci
                        join chore_rules cr on cr.id = ci.chore_rule_id
                        join spaces s on s.id = cr.space_id
                        left join house_memberships hm on hm.id = ci.current_assignee_membership_id
                        left join user_profiles up on up.user_id = hm.user_id
                        left join chore_completions cc on cc.chore_instance_id = ci.id
                        where ci.house_id = :houseId
                          and ci.scheduled_date = :scheduledDate
                        order by ci.created_at asc
                        """)
                .param("houseId", houseId)
                .param("scheduledDate", date)
                .query((rs, rowNum) -> new TodayChoreRecord(
                        UUID.fromString(rs.getString("chore_instance_id")),
                        UUID.fromString(rs.getString("house_id")),
                        rs.getString("title"),
                        rs.getString("space_name"),
                        rs.getString("assignee_name"),
                        rs.getString("status"),
                        rs.getDate("scheduled_date").toLocalDate(),
                        rs.getString("completion_id") != null
                ))
                .list();
    }

    public DailyProgressRecord getDailyProgress(UUID houseId, LocalDate date) {
        return jdbcClient.sql("""
                        select count(*) as total_count,
                               count(case when status = 'COMPLETED' then 1 end) as completed_count
                        from chore_instances
                        where house_id = :houseId
                          and scheduled_date = :scheduledDate
                        """)
                .param("houseId", houseId)
                .param("scheduledDate", date)
                .query((rs, rowNum) -> new DailyProgressRecord(
                        rs.getInt("completed_count"),
                        rs.getInt("total_count")
                ))
                .single();
    }

    public long countPendingActions(UUID userId, UUID houseId, LocalDate date) {
        Long count = jdbcClient.sql("""
                        select count(*)
                        from chore_instances ci
                        join house_memberships hm on hm.id = ci.current_assignee_membership_id
                        where hm.user_id = :userId
                          and ci.house_id = :houseId
                          and ci.scheduled_date = :scheduledDate
                          and ci.status <> 'COMPLETED'
                        """)
                .param("userId", userId)
                .param("houseId", houseId)
                .param("scheduledDate", date)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public Optional<ChoreInstanceRecord> findChoreInstance(UUID choreInstanceId) {
        return jdbcClient.sql("""
                        select id, house_id, chore_rule_id, scheduled_date, status, current_assignee_membership_id
                        from chore_instances
                        where id = :choreInstanceId
                        limit 1
                        """)
                .param("choreInstanceId", choreInstanceId)
                .query((rs, rowNum) -> new ChoreInstanceRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        UUID.fromString(rs.getString("chore_rule_id")),
                        rs.getDate("scheduled_date").toLocalDate(),
                        rs.getString("status"),
                        rs.getString("current_assignee_membership_id") == null ? null : UUID.fromString(rs.getString("current_assignee_membership_id"))
                ))
                .optional();
    }

    public void completeChore(UUID choreInstanceId, UUID completedByUserId, String memo, String proofImageUrl, Instant now) {
        jdbcClient.sql("""
                        insert into chore_completions (
                            id, chore_instance_id, completed_by_user_id, completed_at, memo, proof_image_url, created_at, updated_at
                        )
                        values (:id, :choreInstanceId, :completedByUserId, :completedAt, :memo, :proofImageUrl, :createdAt, :updatedAt)
                        on conflict (chore_instance_id)
                        do update set completed_by_user_id = excluded.completed_by_user_id,
                                      completed_at = excluded.completed_at,
                                      memo = excluded.memo,
                                      proof_image_url = excluded.proof_image_url,
                                      updated_at = excluded.updated_at
                        """)
                .param("id", UUID.randomUUID())
                .param("choreInstanceId", choreInstanceId)
                .param("completedByUserId", completedByUserId)
                .param("completedAt", Timestamp.from(now))
                .param("memo", memo)
                .param("proofImageUrl", proofImageUrl)
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();

        jdbcClient.sql("""
                        update chore_instances
                        set status = 'COMPLETED',
                            updated_at = :updatedAt
                        where id = :choreInstanceId
                        """)
                .param("choreInstanceId", choreInstanceId)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void cancelCompletion(UUID choreInstanceId, Instant now) {
        jdbcClient.sql("delete from chore_completions where chore_instance_id = :choreInstanceId")
                .param("choreInstanceId", choreInstanceId)
                .update();

        jdbcClient.sql("""
                        update chore_instances
                        set status = 'PENDING',
                            updated_at = :updatedAt
                        where id = :choreInstanceId
                        """)
                .param("choreInstanceId", choreInstanceId)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void reassignChoreInstance(UUID choreInstanceId, UUID assigneeMembershipId, Instant now) {
        jdbcClient.sql("""
                        update chore_instances
                        set current_assignee_membership_id = :assigneeMembershipId,
                            origin_type = 'ADJUSTED',
                            updated_at = :updatedAt
                        where id = :choreInstanceId
                        """)
                .param("choreInstanceId", choreInstanceId)
                .param("assigneeMembershipId", assigneeMembershipId)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void rescheduleChoreInstance(UUID choreInstanceId, LocalDate scheduledDate, Instant now) {
        jdbcClient.sql("""
                        update chore_instances
                        set scheduled_date = :scheduledDate,
                            status = 'RESCHEDULED',
                            origin_type = 'ADJUSTED',
                            updated_at = :updatedAt
                        where id = :choreInstanceId
                        """)
                .param("choreInstanceId", choreInstanceId)
                .param("scheduledDate", scheduledDate)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public record ChoreRuleRecord(
            UUID choreRuleId,
            UUID houseId,
            UUID spaceId,
            String title,
            String description,
            UUID defaultAssigneeMembershipId,
            Integer estimatedMinutes
    ) {
    }

    public record TodayChoreRecord(
            UUID choreInstanceId,
            UUID houseId,
            String title,
            String spaceName,
            String assigneeName,
            String status,
            LocalDate scheduledDate,
            boolean completed
    ) {
    }

    public record DailyProgressRecord(
            int completedCount,
            int totalCount
    ) {
    }

    public record ChoreInstanceRecord(
            UUID choreInstanceId,
            UUID houseId,
            UUID choreRuleId,
            LocalDate scheduledDate,
            String status,
            UUID currentAssigneeMembershipId
    ) {
    }
}

package com.fourohfour.backend.modules.adjustment.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AdjustmentJdbcRepository {

    private final JdbcClient jdbcClient;

    public AdjustmentJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void createRequest(
            UUID adjustmentRequestId,
            UUID houseId,
            UUID choreInstanceId,
            UUID requesterMembershipId,
            String requestType,
            String reason,
            LocalDate requestedDate,
            Instant expiresAt,
            Instant now
    ) {
        jdbcClient.sql("""
                        insert into adjustment_requests (
                            id, house_id, chore_instance_id, requester_membership_id, request_type, reason, requested_date, status, expires_at, created_at, updated_at
                        )
                        values (:id, :houseId, :choreInstanceId, :requesterMembershipId, :requestType, :reason, :requestedDate, 'OPEN', :expiresAt, :createdAt, :updatedAt)
                        """)
                .param("id", adjustmentRequestId)
                .param("houseId", houseId)
                .param("choreInstanceId", choreInstanceId)
                .param("requesterMembershipId", requesterMembershipId)
                .param("requestType", requestType)
                .param("reason", reason)
                .param("requestedDate", requestedDate)
                .param("expiresAt", expiresAt == null ? null : Timestamp.from(expiresAt))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public Optional<AdjustmentRequestRecord> findRequest(UUID adjustmentRequestId) {
        return jdbcClient.sql("""
                        select id, house_id, chore_instance_id, requester_membership_id, request_type, reason, requested_date, status, expires_at
                        from adjustment_requests
                        where id = :adjustmentRequestId
                        limit 1
                        """)
                .param("adjustmentRequestId", adjustmentRequestId)
                .query((rs, rowNum) -> new AdjustmentRequestRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        UUID.fromString(rs.getString("chore_instance_id")),
                        UUID.fromString(rs.getString("requester_membership_id")),
                        rs.getString("request_type"),
                        rs.getString("reason"),
                        rs.getDate("requested_date") == null ? null : rs.getDate("requested_date").toLocalDate(),
                        rs.getString("status"),
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant()
                ))
                .optional();
    }

    public List<AdjustmentRequestSummaryRecord> listOpenRequests(UUID houseId) {
        return jdbcClient.sql("""
                        select ar.id,
                               ar.house_id,
                               ar.chore_instance_id,
                               ar.requester_membership_id,
                               ar.request_type,
                               ar.reason,
                               ar.requested_date,
                               ar.status,
                               ar.expires_at,
                               cr.title as chore_title,
                               up.nickname as requester_name
                        from adjustment_requests ar
                        join chore_instances ci on ci.id = ar.chore_instance_id
                        join chore_rules cr on cr.id = ci.chore_rule_id
                        join house_memberships hm on hm.id = ar.requester_membership_id
                        join user_profiles up on up.user_id = hm.user_id
                        where ar.house_id = :houseId
                          and ar.status = 'OPEN'
                        order by ar.created_at desc
                        """)
                .param("houseId", houseId)
                .query((rs, rowNum) -> new AdjustmentRequestSummaryRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        UUID.fromString(rs.getString("chore_instance_id")),
                        UUID.fromString(rs.getString("requester_membership_id")),
                        rs.getString("request_type"),
                        rs.getString("reason"),
                        rs.getDate("requested_date") == null ? null : rs.getDate("requested_date").toLocalDate(),
                        rs.getString("status"),
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                        rs.getString("chore_title"),
                        rs.getString("requester_name")
                ))
                .list();
    }

    public void updateStatus(UUID adjustmentRequestId, String status, Instant now) {
        jdbcClient.sql("""
                        update adjustment_requests
                        set status = :status,
                            updated_at = :updatedAt
                        where id = :adjustmentRequestId
                        """)
                .param("adjustmentRequestId", adjustmentRequestId)
                .param("status", status)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void createResponse(UUID responseId, UUID adjustmentRequestId, UUID responderMembershipId, String decision, Instant now) {
        jdbcClient.sql("""
                        insert into adjustment_responses (
                            id, adjustment_request_id, responder_membership_id, decision, responded_at, created_at, updated_at
                        )
                        values (:id, :adjustmentRequestId, :responderMembershipId, :decision, :respondedAt, :createdAt, :updatedAt)
                        """)
                .param("id", responseId)
                .param("adjustmentRequestId", adjustmentRequestId)
                .param("responderMembershipId", responderMembershipId)
                .param("decision", decision)
                .param("respondedAt", Timestamp.from(now))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void incrementRewardCounter(UUID membershipId, Instant now) {
        Integer updatedRows = jdbcClient.sql("""
                        update adjustment_reward_counters
                        set accepted_substitute_count = accepted_substitute_count + 1,
                            updated_at = :updatedAt
                        where membership_id = :membershipId
                        """)
                .param("membershipId", membershipId)
                .param("updatedAt", Timestamp.from(now))
                .update();

        if (updatedRows != null && updatedRows == 0) {
            jdbcClient.sql("""
                            insert into adjustment_reward_counters (
                                id, membership_id, accepted_substitute_count, created_at, updated_at
                            )
                            values (:id, :membershipId, 1, :createdAt, :updatedAt)
                            """)
                    .param("id", UUID.randomUUID())
                    .param("membershipId", membershipId)
                    .param("createdAt", Timestamp.from(now))
                    .param("updatedAt", Timestamp.from(now))
                    .update();
        }
    }

    public record AdjustmentRequestRecord(
            UUID adjustmentRequestId,
            UUID houseId,
            UUID choreInstanceId,
            UUID requesterMembershipId,
            String requestType,
            String reason,
            LocalDate requestedDate,
            String status,
            Instant expiresAt
    ) {
    }

    public record AdjustmentRequestSummaryRecord(
            UUID adjustmentRequestId,
            UUID houseId,
            UUID choreInstanceId,
            UUID requesterMembershipId,
            String requestType,
            String reason,
            LocalDate requestedDate,
            String status,
            Instant expiresAt,
            String choreTitle,
            String requesterName
    ) {
    }
}


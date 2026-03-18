package com.fourohfour.backend.packages.events;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxJdbcRepository {

    private final JdbcClient jdbcClient;

    public OutboxJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void appendEvent(String aggregateType, UUID aggregateId, String eventType, String payloadJson, Instant now) {
        jdbcClient.sql("""
                        insert into outbox_events (
                            id, aggregate_type, aggregate_id, event_type, payload_json, status, available_at, created_at, updated_at
                        )
                        values (:id, :aggregateType, :aggregateId, :eventType, cast(:payloadJson as jsonb), 'PENDING', :availableAt, :createdAt, :updatedAt)
                        """)
                .param("id", UUID.randomUUID())
                .param("aggregateType", aggregateType)
                .param("aggregateId", aggregateId)
                .param("eventType", eventType)
                .param("payloadJson", payloadJson)
                .param("availableAt", Timestamp.from(now))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public List<OutboxEventRecord> listPendingEvents(int limit, Instant now) {
        return jdbcClient.sql("""
                        select id, aggregate_type, aggregate_id, event_type, payload_json::text as payload_json, available_at, created_at
                        from outbox_events
                        where status = 'PENDING'
                          and available_at <= :now
                        order by created_at asc
                        limit :limit
                        """)
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .query((rs, rowNum) -> new OutboxEventRecord(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("aggregate_type"),
                        UUID.fromString(rs.getString("aggregate_id")),
                        rs.getString("event_type"),
                        rs.getString("payload_json"),
                        rs.getTimestamp("available_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .list();
    }

    public void markDispatched(UUID outboxEventId, Instant now) {
        jdbcClient.sql("""
                        update outbox_events
                        set status = 'DISPATCHED',
                            dispatched_at = :dispatchedAt,
                            updated_at = :updatedAt
                        where id = :id
                        """)
                .param("id", outboxEventId)
                .param("dispatchedAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void markFailed(UUID outboxEventId, Instant availableAt, Instant now) {
        jdbcClient.sql("""
                        update outbox_events
                        set status = 'PENDING',
                            available_at = :availableAt,
                            updated_at = :updatedAt
                        where id = :id
                        """)
                .param("id", outboxEventId)
                .param("availableAt", Timestamp.from(availableAt))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public record OutboxEventRecord(
            UUID outboxEventId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payloadJson,
            Instant availableAt,
            Instant createdAt
    ) {
    }
}

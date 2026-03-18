package com.fourohfour.backend.modules.notification.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationJdbcRepository {

    private final JdbcClient jdbcClient;

    public NotificationJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public UUID createNotification(UUID houseId, UUID actorUserId, String type, String title, String body, String payloadJson, Instant occurredAt, Instant now) {
        UUID notificationId = UUID.randomUUID();
        jdbcClient.sql("""
                        insert into notifications (
                            id, house_id, actor_user_id, type, title, body, payload_json, occurred_at, created_at, updated_at
                        )
                        values (:id, :houseId, :actorUserId, :type, :title, :body, cast(:payloadJson as jsonb), :occurredAt, :createdAt, :updatedAt)
                        """)
                .param("id", notificationId)
                .param("houseId", houseId)
                .param("actorUserId", actorUserId)
                .param("type", type)
                .param("title", title)
                .param("body", body)
                .param("payloadJson", payloadJson)
                .param("occurredAt", Timestamp.from(occurredAt))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
        return notificationId;
    }

    public void createReceiptsForHouseMembers(UUID notificationId, UUID houseId, Instant now) {
        jdbcClient.sql("""
                        insert into notification_receipts (
                            id, notification_id, user_id, read_at, hidden_at, created_at, updated_at
                        )
                        select gen_random_uuid(), :notificationId, hm.user_id, null, null, :createdAt, :updatedAt
                        from house_memberships hm
                        where hm.house_id = :houseId
                          and hm.status = 'ACTIVE'
                        """)
                .param("notificationId", notificationId)
                .param("houseId", houseId)
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public List<NotificationRecord> listNotifications(UUID userId, UUID houseId, Instant cursor, int limit) {
        String baseSql = """
                select n.id,
                       n.house_id,
                       n.type,
                       n.title,
                       n.body,
                       n.payload_json::text as payload_json,
                       n.occurred_at,
                       nr.read_at
                from notifications n
                left join notification_receipts nr
                  on nr.notification_id = n.id
                 and nr.user_id = :userId
                where n.house_id = :houseId
                """;

        JdbcClient.StatementSpec statementSpec;
        if (cursor == null) {
            statementSpec = jdbcClient.sql(baseSql + """
                    order by n.occurred_at desc, n.created_at desc
                    limit :limit
                    """);
        } else {
            statementSpec = jdbcClient.sql(baseSql + """
                      and n.occurred_at < :cursor
                    order by n.occurred_at desc, n.created_at desc
                    limit :limit
                    """)
                    .param("cursor", Timestamp.from(cursor));
        }

        return statementSpec
                .param("userId", userId)
                .param("houseId", houseId)
                .param("limit", limit)
                .query((rs, rowNum) -> new NotificationRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getString("payload_json"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getTimestamp("read_at") != null
                ))
                .list();
    }

    public long unreadCount(UUID userId, UUID houseId) {
        Long count = jdbcClient.sql("""
                        select count(*)
                        from notifications n
                        left join notification_receipts nr
                          on nr.notification_id = n.id
                         and nr.user_id = :userId
                        where n.house_id = :houseId
                          and nr.read_at is null
                        """)
                .param("userId", userId)
                .param("houseId", houseId)
                .query(Long.class)
                .single();
        return count == null ? 0L : count;
    }

    public void markAsRead(UUID notificationId, UUID userId, Instant now) {
        Integer updated = jdbcClient.sql("""
                        update notification_receipts
                        set read_at = coalesce(read_at, :readAt),
                            updated_at = :updatedAt
                        where notification_id = :notificationId
                          and user_id = :userId
                        """)
                .param("notificationId", notificationId)
                .param("userId", userId)
                .param("readAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();

        if (updated != null && updated == 0) {
            jdbcClient.sql("""
                            insert into notification_receipts (
                                id, notification_id, user_id, read_at, hidden_at, created_at, updated_at
                            )
                            values (:id, :notificationId, :userId, :readAt, null, :createdAt, :updatedAt)
                            """)
                    .param("id", UUID.randomUUID())
                    .param("notificationId", notificationId)
                    .param("userId", userId)
                    .param("readAt", Timestamp.from(now))
                    .param("createdAt", Timestamp.from(now))
                    .param("updatedAt", Timestamp.from(now))
                    .update();
        }
    }

    public void registerPushDevice(UUID userId, String platform, String pushToken, String deviceId, Instant now) {
        Integer updated = jdbcClient.sql("""
                        update push_devices
                        set user_id = :userId,
                            platform = :platform,
                            status = 'ACTIVE',
                            last_seen_at = :lastSeenAt,
                            updated_at = :updatedAt
                        where push_token = :pushToken
                        """)
                .param("userId", userId)
                .param("platform", platform)
                .param("pushToken", pushToken)
                .param("lastSeenAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();

        if (updated != null && updated == 0) {
            jdbcClient.sql("""
                            insert into push_devices (
                                id, user_id, platform, push_token, status, last_seen_at, created_at, updated_at
                            )
                            values (:id, :userId, :platform, :pushToken, 'ACTIVE', :lastSeenAt, :createdAt, :updatedAt)
                            """)
                    .param("id", UUID.randomUUID())
                    .param("userId", userId)
                    .param("platform", platform)
                    .param("pushToken", pushToken)
                    .param("lastSeenAt", Timestamp.from(now))
                    .param("createdAt", Timestamp.from(now))
                    .param("updatedAt", Timestamp.from(now))
                    .update();
        }
    }

    public void deactivatePushDevice(UUID userId, UUID deviceId, Instant now) {
        jdbcClient.sql("""
                        update push_devices
                        set status = 'INACTIVE',
                            updated_at = :updatedAt
                        where id = :deviceId
                          and user_id = :userId
                        """)
                .param("deviceId", deviceId)
                .param("userId", userId)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public record NotificationRecord(
            UUID notificationId,
            UUID houseId,
            String type,
            String title,
            String body,
            String payloadJson,
            Instant occurredAt,
            boolean isRead
    ) {
    }
}

package com.fourohfour.backend.modules.user.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserJdbcRepository {

    private final JdbcClient jdbcClient;

    public UserJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<UserProfileRecord> findByUserId(UUID userId) {
        return jdbcClient.sql("""
                        select u.id as user_id,
                               u.primary_email,
                               up.nickname,
                               up.profile_image_url
                        from users u
                        join user_profiles up on up.user_id = u.id
                        where u.id = :userId
                          and u.status = 'ACTIVE'
                        """)
                .param("userId", userId)
                .query((rs, rowNum) -> new UserProfileRecord(
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("nickname"),
                        rs.getString("profile_image_url"),
                        rs.getString("primary_email")
                ))
                .optional();
    }

    public void updateProfile(UUID userId, String nickname, String profileImageUrl, Instant now) {
        jdbcClient.sql("""
                        update user_profiles
                        set nickname = :nickname,
                            profile_image_url = :profileImageUrl,
                            updated_at = :updatedAt
                        where user_id = :userId
                        """)
                .param("userId", userId)
                .param("nickname", nickname)
                .param("profileImageUrl", profileImageUrl)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void markWithdrawn(UUID userId, Instant now) {
        jdbcClient.sql("""
                        update users
                        set status = 'WITHDRAWN',
                            updated_at = :updatedAt
                        where id = :userId
                        """)
                .param("userId", userId)
                .param("updatedAt", Timestamp.from(now))
                .update();

        jdbcClient.sql("""
                        insert into withdrawal_records (
                            id, user_id, reason, detail, withdrawn_at, created_at, updated_at
                        )
                        values (:id, :userId, :reason, :detail, :withdrawnAt, :createdAt, :updatedAt)
                        """)
                .param("id", UUID.randomUUID())
                .param("userId", userId)
                .param("reason", "USER_REQUEST")
                .param("detail", null)
                .param("withdrawnAt", Timestamp.from(now))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public record UserProfileRecord(
            UUID userId,
            String nickname,
            String profileImageUrl,
            String email
    ) {
    }
}


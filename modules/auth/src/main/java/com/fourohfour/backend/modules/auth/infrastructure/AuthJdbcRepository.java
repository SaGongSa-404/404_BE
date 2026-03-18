package com.fourohfour.backend.modules.auth.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuthJdbcRepository {

    private final JdbcClient jdbcClient;

    public AuthJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<SocialAccountRecord> findKakaoAccount(String providerUserId) {
        return jdbcClient.sql("""
                        select user_id
                        from social_accounts
                        where provider = 'KAKAO'
                          and provider_user_id = :providerUserId
                        limit 1
                        """)
                .param("providerUserId", providerUserId)
                .query((rs, rowNum) -> new SocialAccountRecord(UUID.fromString(rs.getString("user_id"))))
                .optional();
    }

    public void createUser(UUID userId, String email, Instant now) {
        jdbcClient.sql("""
                        insert into users (id, status, primary_email, last_login_at, created_at, updated_at)
                        values (:id, 'ACTIVE', :email, :lastLoginAt, :createdAt, :updatedAt)
                        """)
                .param("id", userId)
                .param("email", email)
                .param("lastLoginAt", Timestamp.from(now))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void createUserProfile(UUID profileId, UUID userId, String nickname, String profileImageUrl, Instant now) {
        jdbcClient.sql("""
                        insert into user_profiles (id, user_id, nickname, profile_image_url, created_at, updated_at)
                        values (:id, :userId, :nickname, :profileImageUrl, :createdAt, :updatedAt)
                        """)
                .param("id", profileId)
                .param("userId", userId)
                .param("nickname", nickname)
                .param("profileImageUrl", profileImageUrl)
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void createSocialAccount(UUID socialAccountId, UUID userId, String providerUserId, String email, Instant now) {
        jdbcClient.sql("""
                        insert into social_accounts (
                            id, user_id, provider, provider_user_id, email, connected_at, last_login_at, created_at, updated_at
                        )
                        values (:id, :userId, 'KAKAO', :providerUserId, :email, :connectedAt, :lastLoginAt, :createdAt, :updatedAt)
                        """)
                .param("id", socialAccountId)
                .param("userId", userId)
                .param("providerUserId", providerUserId)
                .param("email", email)
                .param("connectedAt", Timestamp.from(now))
                .param("lastLoginAt", Timestamp.from(now))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void touchLogin(UUID userId, String providerUserId, Instant now) {
        jdbcClient.sql("""
                        update users
                        set last_login_at = :lastLoginAt,
                            updated_at = :updatedAt
                        where id = :userId
                        """)
                .param("userId", userId)
                .param("lastLoginAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();

        jdbcClient.sql("""
                        update social_accounts
                        set last_login_at = :lastLoginAt,
                            updated_at = :updatedAt
                        where provider = 'KAKAO'
                          and provider_user_id = :providerUserId
                        """)
                .param("providerUserId", providerUserId)
                .param("lastLoginAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void createSession(
            UUID sessionId,
            UUID userId,
            String refreshTokenHash,
            String deviceId,
            String deviceName,
            Instant expiresAt,
            Instant now
    ) {
        jdbcClient.sql("""
                        insert into auth_sessions (
                            id, user_id, refresh_token_hash, device_id, device_name, status, expires_at, created_at, updated_at
                        )
                        values (:id, :userId, :refreshTokenHash, :deviceId, :deviceName, 'ACTIVE', :expiresAt, :createdAt, :updatedAt)
                        """)
                .param("id", sessionId)
                .param("userId", userId)
                .param("refreshTokenHash", refreshTokenHash)
                .param("deviceId", deviceId)
                .param("deviceName", deviceName)
                .param("expiresAt", Timestamp.from(expiresAt))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public Optional<SessionRecord> findActiveSessionByTokenHash(String refreshTokenHash) {
        return jdbcClient.sql("""
                        select id, user_id, expires_at
                        from auth_sessions
                        where refresh_token_hash = :refreshTokenHash
                          and status = 'ACTIVE'
                        limit 1
                        """)
                .param("refreshTokenHash", refreshTokenHash)
                .query((rs, rowNum) -> new SessionRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("user_id")),
                        rs.getTimestamp("expires_at").toInstant()
                ))
                .optional();
    }

    public void rotateSession(UUID sessionId, String refreshTokenHash, Instant expiresAt, Instant now) {
        jdbcClient.sql("""
                        update auth_sessions
                        set refresh_token_hash = :refreshTokenHash,
                            expires_at = :expiresAt,
                            updated_at = :updatedAt
                        where id = :sessionId
                        """)
                .param("sessionId", sessionId)
                .param("refreshTokenHash", refreshTokenHash)
                .param("expiresAt", Timestamp.from(expiresAt))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void revokeSession(UUID sessionId, Instant now) {
        jdbcClient.sql("""
                        update auth_sessions
                        set status = 'REVOKED',
                            revoked_at = :revokedAt,
                            updated_at = :updatedAt
                        where id = :sessionId
                        """)
                .param("sessionId", sessionId)
                .param("revokedAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public AuthenticatedUserRecord getAuthenticatedUser(UUID userId) {
        return jdbcClient.sql("""
                        select u.id as user_id,
                               u.primary_email,
                               up.nickname,
                               up.profile_image_url
                        from users u
                        join user_profiles up on up.user_id = u.id
                        where u.id = :userId
                        """)
                .param("userId", userId)
                .query((rs, rowNum) -> new AuthenticatedUserRecord(
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("nickname"),
                        rs.getString("profile_image_url"),
                        rs.getString("primary_email")
                ))
                .single();
    }

    public Set<String> findAgreedRequiredTerms(UUID userId) {
        return new LinkedHashSet<>(jdbcClient.sql("""
                        select terms_type
                        from terms_agreements
                        where user_id = :userId
                          and is_required = true
                        order by agreed_at asc
                        """)
                .param("userId", userId)
                .query(String.class)
                .list());
    }

    public void saveTermsAgreement(UUID agreementId, UUID userId, String termsType, String termsVersion, boolean required, Instant now) {
        jdbcClient.sql("""
                        insert into terms_agreements (
                            id, user_id, terms_type, terms_version, is_required, agreed_at, created_at, updated_at
                        )
                        values (:id, :userId, :termsType, :termsVersion, :required, :agreedAt, :createdAt, :updatedAt)
                        """)
                .param("id", agreementId)
                .param("userId", userId)
                .param("termsType", termsType)
                .param("termsVersion", termsVersion)
                .param("required", required)
                .param("agreedAt", Timestamp.from(now))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public record SocialAccountRecord(UUID userId) {
    }

    public record SessionRecord(
            UUID sessionId,
            UUID userId,
            Instant expiresAt
    ) {
    }

    public record AuthenticatedUserRecord(
            UUID userId,
            String nickname,
            String profileImageUrl,
            String email
    ) {
    }
}


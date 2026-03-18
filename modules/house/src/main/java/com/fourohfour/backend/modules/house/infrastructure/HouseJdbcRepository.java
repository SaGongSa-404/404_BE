package com.fourohfour.backend.modules.house.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class HouseJdbcRepository {

    private final JdbcClient jdbcClient;

    public HouseJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void createHouse(UUID houseId, String name, Instant now) {
        jdbcClient.sql("""
                        insert into houses (id, owner_membership_id, name, status, created_at, updated_at)
                        values (:id, null, :name, 'ACTIVE', :createdAt, :updatedAt)
                        """)
                .param("id", houseId)
                .param("name", name)
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void createMembership(UUID membershipId, UUID houseId, UUID userId, String role, Instant now) {
        jdbcClient.sql("""
                        insert into house_memberships (
                            id, house_id, user_id, role, status, joined_at, created_at, updated_at
                        )
                        values (:id, :houseId, :userId, :role, 'ACTIVE', :joinedAt, :createdAt, :updatedAt)
                        """)
                .param("id", membershipId)
                .param("houseId", houseId)
                .param("userId", userId)
                .param("role", role)
                .param("joinedAt", Timestamp.from(now))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void assignOwnerMembership(UUID houseId, UUID membershipId, Instant now) {
        jdbcClient.sql("""
                        update houses
                        set owner_membership_id = :membershipId,
                            updated_at = :updatedAt
                        where id = :houseId
                        """)
                .param("houseId", houseId)
                .param("membershipId", membershipId)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void createInviteCode(UUID inviteCodeId, UUID houseId, String code, UUID createdByUserId, Instant now) {
        jdbcClient.sql("""
                        insert into invite_codes (
                            id, house_id, code, status, expires_at, created_by_user_id, created_at, updated_at
                        )
                        values (:id, :houseId, :code, 'ACTIVE', null, :createdByUserId, :createdAt, :updatedAt)
                        """)
                .param("id", inviteCodeId)
                .param("houseId", houseId)
                .param("code", code)
                .param("createdByUserId", createdByUserId)
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public Optional<ActiveHouseRecord> findActiveHouseByUserId(UUID userId) {
        return jdbcClient.sql("""
                        select h.id as house_id,
                               h.name,
                               h.cleanliness_level,
                               count(hm2.id) as member_count
                        from house_memberships hm
                        join houses h on h.id = hm.house_id
                        left join house_memberships hm2
                          on hm2.house_id = h.id
                         and hm2.status = 'ACTIVE'
                        where hm.user_id = :userId
                          and hm.status = 'ACTIVE'
                        group by h.id, h.name, h.cleanliness_level, hm.joined_at
                        order by hm.joined_at desc
                        limit 1
                        """)
                .param("userId", userId)
                .query((rs, rowNum) -> new ActiveHouseRecord(
                        UUID.fromString(rs.getString("house_id")),
                        rs.getString("name"),
                        rs.getString("cleanliness_level"),
                        rs.getInt("member_count")
                ))
                .optional();
    }

    public Optional<ActiveHouseRecord> findHouseSummaryByHouseId(UUID houseId) {
        return jdbcClient.sql("""
                        select h.id as house_id,
                               h.name,
                               h.cleanliness_level,
                               count(hm.id) as member_count
                        from houses h
                        left join house_memberships hm
                          on hm.house_id = h.id
                         and hm.status = 'ACTIVE'
                        where h.id = :houseId
                        group by h.id, h.name, h.cleanliness_level
                        limit 1
                        """)
                .param("houseId", houseId)
                .query((rs, rowNum) -> new ActiveHouseRecord(
                        UUID.fromString(rs.getString("house_id")),
                        rs.getString("name"),
                        rs.getString("cleanliness_level"),
                        rs.getInt("member_count")
                ))
                .optional();
    }

    public Optional<MembershipRecord> findActiveMembership(UUID houseId, UUID userId) {
        return jdbcClient.sql("""
                        select id, house_id, user_id, role
                        from house_memberships
                        where house_id = :houseId
                          and user_id = :userId
                          and status = 'ACTIVE'
                        limit 1
                        """)
                .param("houseId", houseId)
                .param("userId", userId)
                .query((rs, rowNum) -> new MembershipRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("role")
                ))
                .optional();
    }

    public Optional<MembershipRecord> findActiveMembershipById(UUID membershipId) {
        return jdbcClient.sql("""
                        select id, house_id, user_id, role
                        from house_memberships
                        where id = :membershipId
                          and status = 'ACTIVE'
                        limit 1
                        """)
                .param("membershipId", membershipId)
                .query((rs, rowNum) -> new MembershipRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("role")
                ))
                .optional();
    }

    public Optional<InviteCodeRecord> findActiveInviteCodeByCode(String code) {
        return jdbcClient.sql("""
                        select id, house_id, code, expires_at, status
                        from invite_codes
                        where code = :code
                          and status = 'ACTIVE'
                        limit 1
                        """)
                .param("code", code)
                .query((rs, rowNum) -> new InviteCodeRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        rs.getString("code"),
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                        rs.getString("status")
                ))
                .optional();
    }

    public Optional<InviteCodeRecord> findLatestInviteCodeByHouseId(UUID houseId) {
        return jdbcClient.sql("""
                        select id, house_id, code, expires_at, status
                        from invite_codes
                        where house_id = :houseId
                          and status = 'ACTIVE'
                        order by created_at desc
                        limit 1
                        """)
                .param("houseId", houseId)
                .query((rs, rowNum) -> new InviteCodeRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        rs.getString("code"),
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                        rs.getString("status")
                ))
                .optional();
    }

    public void upsertCleanlinessVote(UUID houseId, UUID userId, String voteLevel, Instant now) {
        jdbcClient.sql("""
                        insert into cleanliness_votes (
                            id, house_id, user_id, vote_level, voted_at, created_at, updated_at
                        )
                        values (:id, :houseId, :userId, :voteLevel, :votedAt, :createdAt, :updatedAt)
                        on conflict (house_id, user_id)
                        do update set vote_level = excluded.vote_level,
                                      voted_at = excluded.voted_at,
                                      updated_at = excluded.updated_at
                        """)
                .param("id", UUID.randomUUID())
                .param("houseId", houseId)
                .param("userId", userId)
                .param("voteLevel", voteLevel)
                .param("votedAt", Timestamp.from(now))
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void updateHouseCleanlinessLevel(UUID houseId, String cleanlinessLevel, Instant now) {
        jdbcClient.sql("""
                        update houses
                        set cleanliness_level = :cleanlinessLevel,
                            updated_at = :updatedAt
                        where id = :houseId
                        """)
                .param("houseId", houseId)
                .param("cleanlinessLevel", cleanlinessLevel)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public List<CleanlinessSummaryRecord> listCleanlinessSummary(UUID houseId) {
        return jdbcClient.sql("""
                        select vote_level, count(*) as vote_count
                        from cleanliness_votes
                        where house_id = :houseId
                        group by vote_level
                        order by vote_count desc, vote_level asc
                        """)
                .param("houseId", houseId)
                .query((rs, rowNum) -> new CleanlinessSummaryRecord(
                        rs.getString("vote_level"),
                        rs.getLong("vote_count")
                ))
                .list();
    }

    public Optional<String> findMyCleanlinessVote(UUID houseId, UUID userId) {
        return jdbcClient.sql("""
                        select vote_level
                        from cleanliness_votes
                        where house_id = :houseId
                          and user_id = :userId
                        limit 1
                        """)
                .param("houseId", houseId)
                .param("userId", userId)
                .query(String.class)
                .optional();
    }

    public void markMembershipLeft(UUID membershipId, Instant now) {
        jdbcClient.sql("""
                        update house_memberships
                        set status = 'LEFT',
                            left_at = :leftAt,
                            updated_at = :updatedAt
                        where id = :membershipId
                        """)
                .param("membershipId", membershipId)
                .param("leftAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public void updateMembershipRole(UUID membershipId, String role, Instant now) {
        jdbcClient.sql("""
                        update house_memberships
                        set role = :role,
                            updated_at = :updatedAt
                        where id = :membershipId
                        """)
                .param("membershipId", membershipId)
                .param("role", role)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public record ActiveHouseRecord(
            UUID houseId,
            String name,
            String cleanlinessLevel,
            int memberCount
    ) {
    }

    public record MembershipRecord(
            UUID membershipId,
            UUID houseId,
            UUID userId,
            String role
    ) {
    }

    public record InviteCodeRecord(
            UUID inviteCodeId,
            UUID houseId,
            String code,
            Instant expiresAt,
            String status
    ) {
    }

    public record CleanlinessSummaryRecord(
            String voteLevel,
            long count
    ) {
    }
}

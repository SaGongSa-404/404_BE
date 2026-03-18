package com.fourohfour.backend.modules.space.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SpaceJdbcRepository {

    private final JdbcClient jdbcClient;

    public SpaceJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public int nextSortOrder(UUID houseId) {
        Integer nextSortOrder = jdbcClient.sql("""
                        select coalesce(max(sort_order), -1) + 1
                        from spaces
                        where house_id = :houseId
                          and status = 'ACTIVE'
                        """)
                .param("houseId", houseId)
                .query(Integer.class)
                .single();
        return nextSortOrder == null ? 0 : nextSortOrder;
    }

    public void createSpace(UUID spaceId, UUID houseId, String name, int sortOrder, Instant now) {
        jdbcClient.sql("""
                        insert into spaces (
                            id, house_id, name, sort_order, status, created_at, updated_at
                        )
                        values (:id, :houseId, :name, :sortOrder, 'ACTIVE', :createdAt, :updatedAt)
                        """)
                .param("id", spaceId)
                .param("houseId", houseId)
                .param("name", name)
                .param("sortOrder", sortOrder)
                .param("createdAt", Timestamp.from(now))
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public List<SpaceRecord> listSpaces(UUID houseId) {
        return jdbcClient.sql("""
                        select id, house_id, name, sort_order
                        from spaces
                        where house_id = :houseId
                          and status = 'ACTIVE'
                        order by sort_order asc, created_at asc
                        """)
                .param("houseId", houseId)
                .query((rs, rowNum) -> new SpaceRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        rs.getString("name"),
                        rs.getInt("sort_order")
                ))
                .list();
    }

    public Optional<SpaceRecord> findById(UUID spaceId) {
        return jdbcClient.sql("""
                        select id, house_id, name, sort_order
                        from spaces
                        where id = :spaceId
                          and status = 'ACTIVE'
                        limit 1
                        """)
                .param("spaceId", spaceId)
                .query((rs, rowNum) -> new SpaceRecord(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("house_id")),
                        rs.getString("name"),
                        rs.getInt("sort_order")
                ))
                .optional();
    }

    public void renameSpace(UUID spaceId, String name, Instant now) {
        jdbcClient.sql("""
                        update spaces
                        set name = :name,
                            updated_at = :updatedAt
                        where id = :spaceId
                        """)
                .param("spaceId", spaceId)
                .param("name", name)
                .param("updatedAt", Timestamp.from(now))
                .update();
    }

    public List<SpaceChoreCountRecord> listSpaceChoreCounts(UUID houseId) {
        return jdbcClient.sql("""
                        select s.id as space_id,
                               s.name,
                               count(cr.id) as chore_count
                        from spaces s
                        left join chore_rules cr
                          on cr.space_id = s.id
                         and cr.status = 'ACTIVE'
                        where s.house_id = :houseId
                          and s.status = 'ACTIVE'
                        group by s.id, s.name, s.sort_order
                        order by s.sort_order asc, s.name asc
                        """)
                .param("houseId", houseId)
                .query((rs, rowNum) -> new SpaceChoreCountRecord(
                        UUID.fromString(rs.getString("space_id")),
                        rs.getString("name"),
                        rs.getLong("chore_count")
                ))
                .list();
    }

    public record SpaceRecord(
            UUID spaceId,
            UUID houseId,
            String name,
            int sortOrder
    ) {
    }

    public record SpaceChoreCountRecord(
            UUID spaceId,
            String name,
            long choreCount
    ) {
    }
}


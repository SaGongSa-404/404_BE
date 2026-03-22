package com.fourohfour.backend.modules.content.infrastructure;

import com.fourohfour.backend.modules.content.domain.ActionCardCategory;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContentJdbcRepository {

    private static final int URL_MAX_LENGTH = 1000;
    private static final int TITLE_MAX_LENGTH = 255;
    private static final int NOTE_MAX_LENGTH = 1000;
    private static final int SOURCE_DOMAIN_MAX_LENGTH = 120;
    private static final int TAGS_MAX_LENGTH = 500;

    private final JdbcTemplate jdbcTemplate;

    public ContentJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureUser(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from users where id = ?",
                Integer.class,
                userId
        );
        if (count != null && count > 0) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                """
                insert into users (id, nickname, timezone, created_at, updated_at)
                values (?, ?, ?, ?, ?)
                """,
                userId,
                defaultNickname(userId),
                "Asia/Seoul",
                now,
                now
        );
    }

    public SavedContentRecord save(
            UUID userId,
            String url,
            String title,
            String note,
            ActionCardCategory category,
            List<String> tags,
            OffsetDateTime now
    ) {
        UUID contentId = UUID.randomUUID();
        String normalizedUrl = limit(url, URL_MAX_LENGTH);
        String normalizedTitle = limit(title, TITLE_MAX_LENGTH);
        String normalizedNote = limit(note, NOTE_MAX_LENGTH);
        String normalizedDomain = limit(extractDomain(url), SOURCE_DOMAIN_MAX_LENGTH);
        String normalizedTags = tags == null || tags.isEmpty() ? null : limit(String.join(",", tags), TAGS_MAX_LENGTH);

        jdbcTemplate.update(
                """
                insert into saved_contents (
                    id, user_id, url, title, note, source_domain, category, tags_csv, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                contentId,
                userId,
                normalizedUrl,
                normalizedTitle,
                normalizedNote,
                normalizedDomain,
                category.name(),
                normalizedTags,
                now,
                now
        );

        return new SavedContentRecord(contentId, userId, normalizedUrl, normalizedTitle, normalizedNote, normalizedDomain, category, now, tags == null ? List.of() : List.copyOf(tags));
    }

    public SavedContentRecord getById(UUID userId, UUID contentId) {
        List<SavedContentRecord> rows = jdbcTemplate.query(
                """
                select id, user_id, url, title, note, source_domain, category, tags_csv, created_at
                from saved_contents
                where id = ? and user_id = ?
                """,
                (rs, rowNum) -> new SavedContentRecord(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("url"),
                        rs.getString("title"),
                        rs.getString("note"),
                        rs.getString("source_domain"),
                        ActionCardCategory.valueOf(rs.getString("category")),
                        rs.getObject("created_at", OffsetDateTime.class),
                        parseTags(rs.getString("tags_csv"))
                ),
                contentId,
                userId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("저장한 콘텐츠를 찾을 수 없습니다.");
        }
        return rows.getFirst();
    }

    private String defaultNickname(UUID userId) {
        String value = userId.toString().replace("-", "");
        return "user-" + value.substring(0, 6);
    }

    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return uri.getHost().replace("www.", "");
            }
        } catch (URISyntaxException ignored) {
        }
        return "direct";
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private List<String> parseTags(String tagsCsv) {
        if (tagsCsv == null || tagsCsv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tagsCsv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public record SavedContentRecord(
            UUID id,
            UUID userId,
            String url,
            String title,
            String note,
            String sourceDomain,
            ActionCardCategory category,
            OffsetDateTime createdAt,
            List<String> tags
    ) {
    }
}
